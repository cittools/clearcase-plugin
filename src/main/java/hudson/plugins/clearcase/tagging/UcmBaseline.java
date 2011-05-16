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
import hudson.XmlFile;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Project;
import hudson.model.StringParameterValue;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
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
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Notifier;
import hudson.util.CopyOnWriteList;
import hudson.util.DescribableList;
import hudson.util.PersistedList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
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
    /** {@inheritDoc} **/
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        try {
            Project<?, ?> project = (Project<?, ?>) build.getProject();
            insertBaselineBuildStep(project);
            try {
                File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                logger.log("Delayed the baseline creation step *AFTER* the build wrappers.");
            } catch (IOException e1) {
                /* pass */
            }
            return true;
        } catch (Exception e) {
            /* This is an AbstractMavenProject or a MatrixProject
             * therefore, we cannot access the builders field 
             * The baseline creation step will take place now */
            try {
                File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                logger.log("Could not schedule the baseline creation step *AFTER* the build wrappers. " +
                		"Baseline creation will take place *BEFORE* the build wrappers.");
            } catch (IOException e1) {
                /* pass */
            }
            return doPreBuild(build, listener);
        }
    }


    /**
     * The work of this method was originally done by the {@link #prebuild(AbstractBuild, BuildListener)}
     * method. It has been externalized to allow delaying the creation of the baselines after the
     * build wrappers are executed.
     *  
     * @author Robin Jarry
     * @since 2.5.0
     */
    private boolean doPreBuild(AbstractBuild<?, ?> build, BuildListener listener) {
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
                /* In this plugin, the commands are displayed in a separate console "cleartool output"
                 * because cleartool gets sometimes very verbose and it pollutes the build log.
                 * 
                 * By default, Hudson prints every command invoked in the console no matter 
                 * what the user wants. This is done through the getListener().getLogger().printLn() 
                 * method from the Launcher class. 
                 * 
                 * As there is no way to modify this behaviour, I had to create a new launcher 
                 * with a NULL TaskListener so that when Hudson prints something, it goes to 
                 * the trash instead of poping in the middle of the build log */
                Launcher launcher = Executor.currentExecutor().getOwner().getNode().createLauncher(
                        TaskListener.NULL);
                String nodeName = Computer.currentComputer().getName();
                ClearCaseConfiguration ccConfig = scm.fetchClearCaseConfig(nodeName);
                FilePath workspace = scm.getOriginalWorkspace();
                if (workspace == null) {
                    workspace = build.getWorkspace();
                }
                
                ClearTool ct = scm.createClearTool(ccConfig.getCleartoolExe(),
                        workspace, build.getBuiltOn().getRootPath(), 
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
    } // doPreBuild()
    
    
 
    

    /** {@inheritDoc} **/
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher l, BuildListener listener)
            throws InterruptedException, IOException {

        if (build.getProject().getScm() instanceof ClearCaseUcmSCM) {
            try {
                /* In this plugin, the commands are displayed in a separate console "cleartool output"
                 * because cleartool gets sometimes very verbose and it pollutes the build log.
                 * 
                 * By default, Hudson prints every command invoked in the console no matter 
                 * what the user wants. This is done through the getListener().getLogger().printLn() 
                 * method from the Launcher class. 
                 * 
                 * As there is no way to modify this behaviour, I had to create a new launcher 
                 * with a NULL TaskListener so that when Hudson prints something, it goes to 
                 * the trash instead of poping in the middle of the build log */
                Launcher launcher = Executor.currentExecutor().getOwner().getNode().createLauncher(
                        TaskListener.NULL); 
                
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
                        workspace, build.getBuiltOn().getRootPath(), 
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
                
                if (build.getResult().equals(Result.SUCCESS) || build.getResult().equals(Result.UNSTABLE)) {
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
    } // perform()



    

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
    
    /**
     * Insert a dummy UcmBaselineBuildStep at the beginning of a project's build step list.
     * 
     * This is done through Java reflection as the build step list is a private field.
     * 
     * @since 2.5.0
     * @author Robin Jarry
     */
    @SuppressWarnings("unchecked")
    private void insertBaselineBuildStep(Project<?, ?> project) throws NoSuchFieldException,
            SecurityException, IllegalArgumentException, IllegalAccessException
    {
        Field buildersField = Project.class.getDeclaredField("builders");
        buildersField.setAccessible(true);
        DescribableList<Builder, Descriptor<Builder>> builders;
        builders = (DescribableList<Builder, Descriptor<Builder>>) buildersField.get(project);
        List<Builder> modifiedList = new ArrayList<Builder>(builders.toList());
        
        UcmBaselineBuildStep baselineBuildStep = new UcmBaselineBuildStep(this);
        modifiedList.add(0, baselineBuildStep);

        Field dataField = PersistedList.class.getDeclaredField("data");
        dataField.setAccessible(true);
        CopyOnWriteList<Builder> data = (CopyOnWriteList<Builder>) dataField.get(builders);
        data.replaceBy(modifiedList);
    }

    /**
     * Remove any UcmBaselineBuildStep from a project's build step list.
     * 
     * This is done through Java reflection as the build step list is a private field.
     * 
     * @since 2.5.0
     * @author Robin Jarry
     */
    @SuppressWarnings("unchecked")
    private static boolean restoreOriginalBuildSteps(Project<?, ?> project)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException
    {
        boolean modified = false;
        Field buildersField = Project.class.getDeclaredField("builders");
        buildersField.setAccessible(true);
        DescribableList<Builder, Descriptor<Builder>> builders;
        builders = (DescribableList<Builder, Descriptor<Builder>>) buildersField.get(project);
        List<Builder> modifiedList = new ArrayList<Builder>(builders.toList());
        for (Iterator<Builder> it = modifiedList.iterator(); it.hasNext();) {
            Builder b = it.next();
            if (b instanceof UcmBaselineBuildStep) {
                it.remove();
                modified = true;
            }
        }
        if (modified) {
            Field dataField = PersistedList.class.getDeclaredField("data");
            dataField.setAccessible(true);
            CopyOnWriteList<Builder> data = (CopyOnWriteList<Builder>) dataField.get(builders);
            data.replaceBy(modifiedList);
            Logger logger = Logger.getLogger(UcmBaseline.class.getName());
            logger.info("Dummy UcmBaseline builder removed for project: " + project.getName());
        }
        return modified;
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

    
    /*********************
     ** UTILITY CLASSES **
     *********************/
    
    /**
     * This is a dummy build step to allow the baseline creation to take place just BEFORE the 
     * actual build steps and AFTER the build wrappers.
     * 
     * @since 2.5.0
     * @author Robin Jarry
     */
    public static class UcmBaselineBuildStep extends Builder {

        private transient final UcmBaseline publisher;

        @DataBoundConstructor
        public UcmBaselineBuildStep() {
            this(null);
        }

        public UcmBaselineBuildStep(UcmBaseline publisher) {
            super();
            this.publisher = publisher;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher l, BuildListener listener)
                throws InterruptedException, IOException
        {
            if (this.publisher != null) {
                return this.publisher.doPreBuild(build, listener);
            } else {
                return true;
            }
        }

        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @SuppressWarnings("rawtypes")
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return false;
            }

            @Override
            public String getDisplayName() {
                return "UCM Baseline Creation";
            }
        }
    } // class BaselineBuildStep
    
    /**
     * This listener removes any UcmBaselineBuildStep present in the builders section of a 
     * project when a build is complete.
     * 
     * If a build is manually aborted, the build steps are removed anyways.
     * 
     * If an exception occurs, the listener does nothing.
     * 
     * @since 2.5.0
     * @author Robin Jarry
     */
    @SuppressWarnings("rawtypes")
    @Extension
    public static class RestoreBuildStepsListener extends RunListener<AbstractBuild> {

        public RestoreBuildStepsListener() {
            this(AbstractBuild.class);
        }
        
        protected RestoreBuildStepsListener(Class<AbstractBuild> targetType) {
            super(targetType);
        }
        
        @Override
        public void onCompleted(AbstractBuild build, TaskListener listener) {
            try {
                Project<?, ?> project = (Project<?, ?>) build.getProject();
                boolean modified = restoreOriginalBuildSteps(project);
                if (modified) {
                    try {
                        File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                        ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                        logger.log("Restored the original build steps.");
                    } catch (IOException e1) {
                        /* pass */;
                    }
                }
            } catch (Exception e) {
                /* pass */;
            }
        }
    } // class RestoreBuildStepsListener

    /**
     * This listener makes sure that no UcmBaselineBuildStep is present in the builders section
     * of a project when saving.
     * 
     * If an exception occurs, the listeners does nothing.
     * 
     * @since 2.5.0
     * @author Robin Jarry
     */
    @Extension
    public static class OnSaveListener extends SaveableListener {
        
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Project<?, ?>) {
                try {
                    boolean modified = restoreOriginalBuildSteps((Project<?, ?>) o);
                    if (modified) {
                        file.write(o);
                    }
                } catch (Exception e) {
                    /* pass */;
                }
            }
        }
    } // class OnSaveListener
    
    
} // class UcmBaseline

