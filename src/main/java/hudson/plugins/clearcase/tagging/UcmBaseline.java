/**
 * The MIT License
 *
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.clearcase.tagging;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.plugins.clearcase.ClearCaseUcmSCM;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.log.ClearToolLogFile;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.CompositeComponent;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Stream.LockState;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.Tools;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;


/**
 * UcmMakeBaseline creates baselines on a ClearCase stream after a successful
 * build. The name and comment of the baseline can be changed using the
 * namePattern and commentPattern variables.
 * 
 * @author Peter Liljenberg
 * 
 * @author Gregory Boissinot 2008-10-11 Add the rebase dynamic view feature
 *         2008-11-21 Restric the baseline creation on read/write components
 *         2009-03-02 Add the dynamic view support for the make baseline
 *         2009-03-22 The 'createdBaselines' follow now the same model of the
 *         'latestBaselines' and 'readWriteComponents' fields.
 *         
 * @author Robin Jarry
 *         2009-12-18 : Variable resolution on Stream/baseline name
 *         2010-01-18 : Added shiny log flags
 *         2010-01-28 : If baselines are not created/promoted, Build actually fails now
 *         2010-03-04 : Added the publication of the baselines to the build parameters.
 *                      Changed the baseline creation algorithm. Baseline on read-only components 
 *                      are not affected anymore. 
 *         2010-06-30 : Composite baselines are created here now. 
 *                      No more creating baselines only on the read/write components. User will have
 *                      to <b>NOT</b> check "identical" if he doesn't want read only components to
 *                      be affected. This is more clearcase oriented.
 */
public class UcmBaseline extends Notifier {

    /***************
     ** CONSTANTS **
     ***************/
    public static final String ENV_CC_BASELINE = "CC_BASELINE_";
    @Extension
    public static final UcmBaselineDescriptor 
                                 UCM_BL_DESCRIPTOR = new UcmBaselineDescriptor();

    /************
     ** FIELDS **
     ************/
    // transient fields
    private transient List<Baseline> baselines = new ArrayList<Baseline>();
    private transient boolean streamSuccessfullyLocked = false;
    
    // serialized fields
    private final String namePattern; 
    private final String commentPattern;
    
    private final boolean lockStream;
    private final boolean recommend;
    private final boolean fullBaseline;
    private final boolean identical;
    
    private final boolean rebaseDynamicView;
    private final String dynamicViewName;
    
    private final String components;
    
    /*****************
     ** CONSTRUCTOR ** 
     *****************/
    @DataBoundConstructor
    public UcmBaseline(String namePattern,
                       String commentPattern,
                       boolean lockStream,
                       boolean recommend,
                       boolean fullBaseline,
                       boolean identical,
                       boolean rebaseDynamicView,
                       String dynamicViewName,
                       String components)
    {
        super();
        this.namePattern = namePattern;
        this.commentPattern = commentPattern;
        this.lockStream = lockStream;
        this.recommend = recommend;
        this.fullBaseline = fullBaseline;
        this.identical = identical;
        this.rebaseDynamicView = rebaseDynamicView;
        this.dynamicViewName = dynamicViewName;
        this.components = components;
    }

    /**************
     ** OVERRIDE ** 
     **************/ 
    
    @Override
    public UcmBaselineDescriptor getDescriptor() {
        return UcmBaseline.UCM_BL_DESCRIPTOR;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    } 
    
    /******************
     ** MAIN PROCESS ** 
     ******************/ 
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        
        
        if (build.getProject().getScm() instanceof ClearCaseUcmSCM) {
            
            try {
                File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                logger.log("### Begin Baseline creation ###");

                /////// BEGIN VARIABLE RESOLUTION /////////////////////////////////////////////////
                ClearCaseUcmSCM scm = (ClearCaseUcmSCM) build.getProject().getScm();
                EnvVars env = build.getEnvironment(listener);
                
                String baseName = env.expand(namePattern);
                String comment = env.expand(commentPattern);

                // illegal characters removal
                baseName = baseName.replaceAll("[\\s\\\\\\/:\\?\\*\\|]+", "_");
                Pattern p = Pattern.compile("(\\$\\{.+?\\})");
                Matcher match = p.matcher(baseName);
                if (match.find()) {
                    throw new ClearToolError(String.format("Illegal characters found " +
                    		"in baseline name : %s. " +
                    		"An environment variable may not have been resolved.", match.group()));
                }
                /////// END VARIABLE RESOLUTION ///////////////////////////////////////////////////
                
                Launcher launcher = Executor.currentExecutor().getOwner().getNode().createLauncher(
                        listener);
                String nodeName = Computer.currentComputer().getName();
                ClearCaseConfiguration ccConfig = scm.fetchClearCaseConfig(nodeName);
                FilePath workspace = scm.getOriginalWorkspace();
                if (workspace == null) {
                    workspace = build.getWorkspace();
                }
                
                ClearTool ct = scm.createClearTool(ccConfig.getCleartoolExe(),
                        listener, workspace, build.getBuiltOn().getRootPath(), 
                        launcher, env, ctLogFile);

                View view = scm.getView();
                Stream stream = view.getStream();
                
                LockState state = ct.getStreamLockState(stream);
                stream.setLockState(state);
                
                switch (state) {
                case LOCKED:
                    logger.log("WARNING: building on a LOCKED stream. " +
                    		"No baseline will be created.");
                    break;
                case OBSOLETE:
                    logger.log("WARNING: building on an OBSOLETE stream. " +
                            "No baseline will be created.");
                    build.setResult(Result.UNSTABLE);
                    return false;
                default:
                    if (this.lockStream) {
                        logger.log("Locking stream...");
                        try {
                            ct.lockStream(view.getStream());
                            this.streamSuccessfullyLocked = true;
                        } catch (ClearToolError e) {
                            logger.log(e.toString());
                            this.streamSuccessfullyLocked = false;
                        }
                    }
                }
                
                
                /////// BEGIN BASELINE CREATION/RETRIEVAL /////////////////////////////////////////
                List<Component> componentsObj = new ArrayList<Component>();
                List<CompositeComponent> compositeCompObj = new ArrayList<CompositeComponent>();
                if (Util.fixEmptyAndTrim(this.components) != null) {
                    logger.log("Retrieving components details...");
                    String[] compTab = Util.fixEmptyAndTrim(this.components).split("\\s");
                    for (String compName : compTab) {
                        if (Util.fixEmptyAndTrim(compName) != null) {
                            Baseline bl = ct.getLatestBl(new Component(compName), stream);
                            
                            List<Baseline> dependingBls = ct.getDependingBaselines(bl);
                            if (!dependingBls.isEmpty()) {
                                CompositeComponent c_comp = new CompositeComponent(compName);
                                for (Baseline depBl : dependingBls) {
                                    Component depComp = ct.getComponentFromBL(depBl);
                                    c_comp.getAttachedComponents().add(depComp);
                                }
                                compositeCompObj.add(c_comp);
                            } else {
                                Component comp = new Component(compName);
                                componentsObj.add(comp);
                            }
                            
                        }
                    }
                }
                
                
                List<Baseline> createdBls = new ArrayList<Baseline>();
                if (stream.getLockState() == LockState.UNLOCKED) { 
                    logger.log("Creating new baselines...");
                    // creation of the baselines
                    if (identical) {
                        // baselines will be created on every component
                        // no need to separate the composite components from the others.
                        
                        componentsObj.addAll(compositeCompObj);
                        createdBls.addAll(ct.makeBaselines(view, componentsObj, identical, 
                                fullBaseline, baseName, comment));
                    } else {
                        // here we want to create composite baselines even if there were no changes 
                        // in the depending compoents.
                        if (!compositeCompObj.isEmpty()) {
                            for (CompositeComponent c_comp : compositeCompObj) {
                                createdBls.addAll(ct.makeCompositeBaseline(view, c_comp, identical, 
                                        fullBaseline, baseName, comment));
                            }
                            // then create regular baselines if needed
                            if (!componentsObj.isEmpty()) {
                                createdBls.addAll(ct.makeBaselines(view, componentsObj, identical, 
                                        fullBaseline, baseName, comment));
                            }
                        } else {
                            // no composite components were specified
                            createdBls.addAll(ct.makeBaselines(view, componentsObj, identical, 
                                    fullBaseline, baseName, comment));
                        }
                    }
                }
                logger.log("Retrieving latest baselines...");
                // retrieval of the full names of the baselines
                List<Baseline> latestBls = ct.getLatestBaselines(stream);
                
                // get every component attached to the latest baselines
                matchComponentsToBaselines(ct, stream, latestBls);
                // resolve created baselines
                markCreatedBaselines(createdBls, latestBls);
                
                baselines = latestBls;
                
                printUsedBaselines(logger, latestBls);
                
                publishBaselinesAsParams(build, baselines);
                /////// END BASELINE CREATION/RETRIEVAL ///////////////////////////////////////////
           
                logger.log("~~~ End Baseline creation ~~~");
            } catch (ClearToolError ctError) {
                listener.getLogger().println(ctError.toString());
                build.setResult(Result.FAILURE);
                return false;
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                build.setResult(Result.FAILURE);
                return false;
            }
         } else {
            listener.getLogger().println("ERROR: Baselines are only handled by Clearcase UCM.");
            return false;
        }

        return true;
    }
    





    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        if (build.getProject().getScm() instanceof ClearCaseUcmSCM) {
            try {
                File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                
                logger.log("### Begin Baseline promotion ###");
                
                // get all created baselines
                List<Baseline> createdBls = new ArrayList<Baseline>();
                if (this.baselines != null) { // I can't figure out why this can be null here but wtf...
                    for (Baseline bl : this.baselines) {
                        if (bl.isCreated()) {
                            createdBls.add(bl);
                        }
                    }
                }
                
                ClearCaseUcmSCM scm = (ClearCaseUcmSCM) build.getProject().getScm();
                
                EnvVars env = build.getEnvironment(listener);
                
                File logFile = ClearToolLogFile.getCleartoolLogFile(build);
                
                String nodeName = Computer.currentComputer().getName();
                ClearCaseConfiguration ccConfig = scm.fetchClearCaseConfig(nodeName);
                FilePath workspace = scm.getOriginalWorkspace();
                if (workspace == null) {
                    workspace = build.getWorkspace();
                }
                
                ClearTool ct = scm.createClearTool(ccConfig.getCleartoolExe(),
                        listener, workspace, build.getBuiltOn().getRootPath(), 
                        launcher, env, logFile);
                
                View view = scm.getView();
                Stream stream = view.getStream();
                
                if (stream.getLockState() == LockState.LOCKED) {
                    logger.log("The stream is locked, no baseline to promote.");
                    return true;
                }
                
                if (createdBls.isEmpty()) {
                    // this is to handle a hudson issue, when the checkout fails, the job skips the
                    // prebuild step but still performs the baseline promotion...
                    logger.log("No baseline to promote.");
                    if (this.lockStream && this.streamSuccessfullyLocked) {
                        logger.log("Unlocking stream...");
                        ct.unlockStream(stream);
                    }
                    return true;
                }
                
                // get vob dependent promotion levels
                ct.fetchPromotionLevels(stream.getPvob());
                
                if (build.getResult().equals(Result.SUCCESS)) {
                    logger.log("Promoting baselines...");
                    
                    // On success, promote all the baselines that hudson created to "BUILT"
                    for (Baseline bl : createdBls) {
                        ct.changeBaselinePromotionLevel(bl, PromotionLevel.BUILT);
                        logger.log(printPromotedBl(bl, ct.print(PromotionLevel.BUILT)));
                    }
                    
                    if (this.lockStream && this.streamSuccessfullyLocked) {
                        logger.log("Unlocking stream...");
                        ct.unlockStream(stream);
                    }
                    
                    // recommend all baselines that meet the stream's promotion level requirements 
                    if (this.recommend) {
                        logger.log("Recommending baselines...");
                        ct.recommendAllEligibleBaselines(stream);
                    }
                    // Rebase a dynamic view
                    if (this.rebaseDynamicView) {
                        logger.log(String.format("Rebasing view: %s...", 
                                this.dynamicViewName));
                        
                        View dynView = new View(this.dynamicViewName, stream, true);
                        ct.rebaseDynamicView(dynView, createdBls);
                    }
                } else {
                    // On failure, demote all the baselines that hudson created to "REJECTED"
                    logger.log("Rejecting baselines...");
                    for (Baseline bl : createdBls) {
                        ct.changeBaselinePromotionLevel(bl, PromotionLevel.REJECTED);
                        logger.log(printPromotedBl(bl, ct.print(PromotionLevel.REJECTED)));
                    }
                    if (this.lockStream && this.streamSuccessfullyLocked) {
                        logger.log("Unlocking stream...");
                        ct.unlockStream(stream);
                    }
                }
                
                logger.log("~~~ End Baseline promotion ~~~");
                
                
            } catch (ClearToolError ctError) {
                listener.getLogger().println(ctError.toString());
                build.setResult(Result.FAILURE);
                return false;
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                build.setResult(Result.FAILURE);
                return false;
            }
        } else {
            listener.getLogger().println("ERROR: Baselines are only handled by Clearcase UCM.");
            return false;
        }
        
        
        return true;
    }

    /*********************
     ** PRIVATE METHODS ** 
     *********************/
    
    private void printUsedBaselines(ClearCaseLogger logger, List<Baseline> latestBls) {
        for (Baseline bl : latestBls) {
            String blString;
            
            if (bl.isCreated()) {
                blString = "* baseline: %s (on component %s)";
            } else {
                blString = "  baseline: %s (component %s unmodified)";
            }
            
            logger.log(String.format(blString, bl.toString(), bl.getComponent().toString()));
        }
    }

    
    private String printPromotedBl(Baseline baseline, String promotionLevel) {
        return String.format("%s -> '%s'", baseline, promotionLevel);
    }
    
    private void markCreatedBaselines(List<Baseline> createdBls, List<Baseline> latestBls) {
        for (Baseline latestBl : latestBls) {
            for (Baseline createdBl : createdBls) {
                if (latestBl.getName().equals(createdBl.getName())) {
                    latestBl.setCreated(true);
                    break;
                }
            }
        }
    }
    
    private void matchComponentsToBaselines(ClearTool ct, Stream stream, List<Baseline> latestBls) 
            throws IOException, InterruptedException, ClearToolError 
    {
        // retrieval of the components attached to the stream
        stream.setComponents(ct.getComponents(stream));
        
        for (Baseline bl : latestBls) {
            Component comp;

            // get baselines dependencies to detect composite components
            List<Baseline> dependentBls = ct.getDependingBaselines(bl);
            
            if (!dependentBls.isEmpty()) {
                comp = new CompositeComponent(ct.getComponentFromBL(bl));
            } else {
                comp = ct.getComponentFromBL(bl);
            }
            
            // mark read only components
            for (Component streamComp : stream.getComponents()) {
                if (streamComp.equals(comp)) {
                    comp.setReadOnly(streamComp.isReadOnly());
                    break;
                }
            }
            
            bl.setComponent(comp);
        }
    }
    
    /**
     * Publish the names of all the baselines created so that other plugins can use them.
     * 
     * @param build
     *             the build where to publish the parameters
     */
    private void publishBaselinesAsParams(AbstractBuild<?, ?> build, List<Baseline> baselines) {
        if (baselines != null && !baselines.isEmpty()) {
            List<StringParameterValue> parameters = Tools.getCCParameters(build);
            int i = 1;
            for(Baseline bl : baselines){
                String paramKey = ENV_CC_BASELINE + i++;
                
                String description = "";
                if (bl.isCreated()) {
                    description += "<b>CREATED</b> ";
                }
                description += "on component: " + bl.getComponent().toString();
                if (bl.getComponent().isComposite()) {
                    description += " <i><b>(COMPOSITE)</b></i>";
                } else {
                    if (bl.getComponent().isReadOnly()) {
                        description += " <i><b>(READ ONLY)</b></i>";
                    }
                }
                parameters.add(new StringParameterValue(paramKey, bl.toString(), description));
            }
        }
    }

    /*************
     ** GETTERS **
     *************/
    
    public String getNamePattern() {
        return namePattern;
    }

    public String getCommentPattern() {
        return commentPattern;
    }

    public boolean isLockStream() {
        return lockStream;
    }

    public boolean isRecommend() {
        return recommend;
    }

    public boolean isFullBaseline() {
        return fullBaseline;
    }

    public boolean isIdentical() {
        return identical;
    }

    public boolean isRebaseDynamicView() {
        return rebaseDynamicView;
    }

    public String getDynamicViewName() {
        return dynamicViewName;
    }

    public String getComponents() {
        return components;
    }

}
