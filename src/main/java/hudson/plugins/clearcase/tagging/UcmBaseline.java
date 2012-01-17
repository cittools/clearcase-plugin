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
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.StringParameterValue;
import hudson.plugins.clearcase.ClearCaseUcmSCM;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.log.ClearToolLogFile;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.CompositeComponent;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.Stream.LockState;
import hudson.plugins.clearcase.objects.View;
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
 * UcmMakeBaseline creates baselines on a ClearCase stream and changes their promotion level
 * after the build is complete. The name and comment of the baseline can be changed using the
 * namePattern and commentPattern variables.
 * 
 * <h2>Modifcations History</h2>
 * 
 * <ul>
 *   <li><b>2008-10-11:</b><ul><li>Add the rebase dynamic view feature</li></ul></li>
 *   <li><b>2008-11-21:</b><ul><li>Restric the baseline creation on read/write components</li></ul></li>
 *   <li><b>2008-10-11:</b><ul><li>Add the dynamic view support for the make baseline</li></ul></li>
 *   <li><b>2009-12-18:</b><ul><li>Variable resolution on Stream/baseline name</li></ul></li>
 *   <li>
 *      <b>2010-01-18:</b>
 *      <ul>
 *        <li>Added shiny log flags</li>
 *      </ul>
 *   </li>
 *   <li>
 *      <b>2010-01-28:</b>
 *      <ul>
 *        <li>If baselines are not created/promoted, Build actually fails now</li>
 *      </ul>
 *   </li>
 *   <li>
 *      <b>2010-03-04:</b>
 *      <ul>
 *        <li>Added the publication of the baselines to the build parameters.</li>
 *        <li>Changed the baseline creation algorithm. Baseline on read-only components are not affected anymore.</li>
 *      </ul>
 *   </li>
 *   <li>
 *      <b>2010-06-30:</b>
 *      <ul>
 *        <li>Composite baselines are created here now.</li>
 *        <li>No more creating baselines only on the read/write components. The user will have
 *            to <b>NOT</b> check "identical" if he/she doesn't want read only components to
 *            be affected. This is more clearcase oriented.</li>
 *      </ul>
 *   </li>
 *   <li><b>2011-05-17:</b> 
 *      <ul>
 *          <li>Delayed the baseline creation step AFTER the build wrappers.
 *          This was made possible by inserting a dummy "baseline" builder at the 
 *          begining of the build steps of the project ({@link #prebuild(AbstractBuild, BuildListener)}). 
 *          This build step is removed at the end of the build ({@link RestoreBuildStepsListener}), 
 *          or if someone happends to save the project <b>during the build</b> ({@link OnSaveListener})</li>
 *      </ul>
 *   </li>
 *   <li><b>2011-11-29:</b> 
 *      <ul>
 *          <li>The baseline creation is now made after the build. Thanks to the "-time" rules 
 *          that are added in the config spec, we are 100% sure that the view contents have not 
 *          changed during the build. This allows to remove all the hacking made arround dummy
 *          builds steps.</li>
 *          <li>The "Lock Stream" feature has been removed. For the same reasons as above.</li>
 *          <li>Added an option to skip the baseline creation step if the build has failed.</li>
 *      </ul>
 *   </li>
 * </ul>
 *   
 * @author Peter Liljenberg
 * @author Gregory Boissinot
 * @author Robin Jarry
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
    private transient boolean lockStream = false; //@deprecated
    
    // serialized fields
    private final String namePattern; 
    private final String commentPattern;
    
    private final boolean skipOnBuildFailure;
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
                       //boolean lockStream, @deprecated
                       boolean skipOnBuildFailure,
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
        //this.lockStream = lockStream; @deprecated
        this.skipOnBuildFailure = skipOnBuildFailure;
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
    /** {@inheritDoc} **/
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher l, BuildListener listener)
            throws InterruptedException, IOException {

        if (build.getProject().getScm() instanceof ClearCaseUcmSCM) {
            try {
            	File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
            	ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
            	logger.log("### Begin Baseline creation/promotion ###");
            	boolean buildOK = build.getResult().equals(Result.SUCCESS) 
            	        || build.getResult().equals(Result.UNSTABLE);
            	
            	if (!buildOK && this.skipOnBuildFailure) {
            	    logger.log("Build result is " + build.getResult() + ". " + 
            	                "Skipping the baseline creation/promotion step.");
            	    return false;
            	}
            	
            	///// init variables //////////////////////////////////////////////////////////////
                ClearCaseUcmSCM scm = (ClearCaseUcmSCM) build.getProject().getScm();
                EnvVars env = build.getEnvironment(listener);
                String nodeName = Computer.currentComputer().getName();
                ClearCaseConfiguration ccConfig = scm.fetchClearCaseConfig(nodeName);
                FilePath workspace = scm.getOriginalWorkspace();
                if (workspace == null) {
                	workspace = build.getWorkspace();
                }
                ClearTool ct = scm.createClearTool(ccConfig.getCleartoolExe(),
                		workspace, build.getBuiltOn().getRootPath(), env, ctLogFile, null);
                View view = scm.getView();
                Stream stream = view.getStream();
                
                ///// check stream lock state /////////////////////////////////////////////////////
                LockState state = ct.getStreamLockState(stream);
                stream.setLockState(state);
                switch (state) {
                case NONE:
                case UNLOCKED:
                    break;
                default:
                    logger.log("WARNING: building on a '" + state
                            + "' stream. No baseline will be created.");
                    return false;
                }

                
                ///// resolve variables in baseline name //////////////////////////////////////////
                String baseName = env.expand(this.namePattern);
                String comment = env.expand(this.commentPattern);

                /* illegal characters removal */
                baseName = baseName.replaceAll("[\\s\\\\\\/:\\?\\*\\|]+", "_");
                Pattern p = Pattern.compile("(\\$\\{.+?\\})");
                Matcher match = p.matcher(baseName);
                if (match.find()) {
                    throw new ClearToolError(String.format("Illegal characters found " +
                            "in baseline name : %s. " +
                            "An environment variable may not have been resolved.", match.group()));
                }
                
                
                ///// main process ////////////////////////////////////////////////////////////////
                logger.log("Retrieving components details...");
                List<Component> components = resolveComponents(stream, ct);

                logger.log("Creating new baselines...");
                List<Baseline> createdBls = createBaselines(baseName, comment, ct, view, components);

                logger.log("Retrieving latest baselines...");
                /* retrieval of the full names of the baselines */
                List<Baseline> latestBls = ct.getLatestBaselines(stream);
                
                /* get every component attached to the latest baselines */
                matchComponentsToBaselines(ct, stream, latestBls);
                /* resolve created baselines */
                markCreatedBaselines(createdBls, latestBls);
                
                printUsedBaselines(logger, latestBls);
                
                if (createdBls.isEmpty()) {
                    logger.log("No baseline was created.");
                } else {
                    /* get vob dependent promotion levels */
                    ct.fetchPromotionLevels(stream.getPvob());
                    
                    if (buildOK) {
                        logger.log("Promoting created baselines...");
                        
                        /* On success, promote all the baselines that hudson created to "BUILT" */
                        for (Baseline bl : createdBls) {
                            ct.changeBaselinePromotionLevel(bl, PromotionLevel.BUILT);
                            logger.log(printPromotedBl(bl, ct.print(PromotionLevel.BUILT)));
                        }
                        
                        /* recommend all baselines that meet the stream's promotion level requirements */ 
                        if (this.recommend) {
                            logger.log("Recommending created baselines...");
                            ct.recommendAllEligibleBaselines(stream);
                        }
                        /* Rebase a dynamic view */
                        if (this.rebaseDynamicView) {
                            logger.log(String.format("Rebasing view: %s with created baselines...", 
                                    dynamicViewName));
                            
                            View dynView = new View(dynamicViewName, stream, true);
                            ct.rebaseDynamicView(dynView, createdBls);
                        }
                    } else {
                        /* On failure, demote all the baselines that hudson created to "REJECTED" */
                        logger.log("Rejecting created baselines...");
                        for (Baseline bl : createdBls) {
                            ct.changeBaselinePromotionLevel(bl, PromotionLevel.REJECTED);
                            logger.log(printPromotedBl(bl, ct.print(PromotionLevel.REJECTED)));
                        }
                    }
                }
                
                publishBaselinesAsParams(build, latestBls);
                
                logger.log("~~~ End Baseline creation/promotion ~~~");
                
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
    } // perform()



    

    /*********************
     ** PRIVATE METHODS ** 
     *********************/
    
    private List<Component> resolveComponents(Stream stream, ClearTool ct)
            throws IOException, InterruptedException, ClearToolError
    {
        List<Component> components = new ArrayList<Component>();
        if (Util.fixEmptyAndTrim(this.components) != null) {
            
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
                        components.add(c_comp);
                    } else {
                        Component comp = new Component(compName);
                        components.add(comp);
                    }
                }
            }
        }
        return components;
    }

    private List<Baseline> createBaselines(String baseName, String comment, ClearTool ct,
            View view, List<Component> components) 
                    throws IOException, InterruptedException, ClearToolError
    {
        List<Baseline> createdBls = new ArrayList<Baseline>();
        
        /* creation of the baselines */
        if (identical) {
            /* baselines will be created on every component
             * no need to separate the composite components from the others. */
            createdBls.addAll(ct.makeBaselines(view, components, identical, fullBaseline,
                    baseName, comment));
        } else {
            /* here we want to create composite baselines even if there were no changes
             * in the depending components. */
            List<Component> simpleComps = new ArrayList<Component>();
            List<CompositeComponent> compositeComps = new ArrayList<CompositeComponent>();
            for (Component c : components) {
                if (c.isComposite()) {
                    compositeComps.add((CompositeComponent) c);
                } else {
                    simpleComps.add(c);
                }
            }
            if (!compositeComps.isEmpty()) {
                for (CompositeComponent cComp : compositeComps) {
                    createdBls.addAll(ct.makeCompositeBaseline(view, cComp, identical,
                            fullBaseline, baseName, comment));
                }
                /* then create regular baselines if needed */
                if (!simpleComps.isEmpty()) {
                    createdBls.addAll(ct.makeBaselines(view, simpleComps, identical,
                            fullBaseline, baseName, comment));
                }
            } else {
                /* no composite components were specified */
                createdBls.addAll(ct.makeBaselines(view, simpleComps, identical, fullBaseline,
                        baseName, comment));
            }
        }
        return createdBls;
    }
    
    
    
    private void printUsedBaselines(ClearCaseLogger logger, List<Baseline> latestBls) {
        for (Baseline bl : latestBls) {
            String blString;
            
            if (bl.isCreated()) {
                blString = "* baseline: %s (created on component %s)";
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
                    /* we transfer the component & pvob information here so they can be used for
                     * the promotion and recomendation of the created baselines later. */
                    createdBl.setComponent(latestBl.getComponent());
                    createdBl.setPvob(latestBl.getPvob());
                    break;
                }
            }
        }
    }
    
    private void matchComponentsToBaselines(ClearTool ct, Stream stream, List<Baseline> latestBls) 
            throws IOException, InterruptedException, ClearToolError 
    {
        /* retrieval of the components attached to the stream */
        stream.setComponents(ct.getComponents(stream));
        
        for (Baseline bl : latestBls) {
            Component comp;

            /* get baselines dependencies to detect composite components */
            List<Baseline> dependentBls = ct.getDependingBaselines(bl);
            
            if (!dependentBls.isEmpty()) {
                comp = new CompositeComponent(ct.getComponentFromBL(bl));
            } else {
                comp = ct.getComponentFromBL(bl);
            }
            
            /* mark read only components */
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

    public boolean isSkipOnBuildFailure() {
        return skipOnBuildFailure;
    }
    
} // class UcmBaseline

