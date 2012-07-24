package hudson.plugins.clearcase.deliver;

import static hudson.plugins.clearcase.AbstractClearCaseSCM.ORIGINAL_WORKSPACE_ENVSTR;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.StringParameterValue;
import hudson.plugins.clearcase.ClearCaseUcmSCM;
import hudson.plugins.clearcase.ClearCaseUcmTooledUpSCM;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.log.ClearToolLogFile;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.CCParametersAction;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.DeliverError;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.File;
import java.io.IOException;

public class BaselineDeliverWrapper extends BuildWrapper {

    public BaselineDeliverWrapper() {
        // TODO Auto-generated constructor stub
    }

    @SuppressWarnings("rawtypes")
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException
    {
        if (build.getProject().getScm() instanceof ClearCaseUcmTooledUpSCM) {
            try {

                File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);

                StringParameterValue buildParameter = CCParametersAction.getBuildParameter(build,
                        ClearCaseUcmTooledUpSCM.CLEARCASE_BUILT_BASELINE_ENVSTR);

                String builtBaseline = null;
                if (buildParameter != null) {
                    builtBaseline = buildParameter.value;
                } else {
                    /* return an empty environment (no deliver needed) */
                    return new DeliverEnvironment();
                }

                logger.log("### Begin baseline deliver ###");

                // /// init variables //////////////////////////////////////////////////////////////
                ClearTool ct = createCleartool(build, listener, ctLogFile);
                View view = ClearCaseUcmSCM.getBuildView(build);

                Baseline baseline = ct.baselineDetails(builtBaseline);

                logger.log("Delivering baseline " + baseline + " to stream " + view.getStream()
                        + "...");
                logger.log("Source stream: " + baseline.getStream());

                try {
                    ct.deliver(baseline, view, true);
                } catch (DeliverError e) {
                    logger.log("Deliver error: " + e.getMessage());
                    build.setResult(Result.FAILURE);
                    build.setDescription(e.getMessage());
                    ct.deliverCancel(baseline.getStream(), view);
                    return null;
                }

                logger.log("Deliver successful.");

                return new DeliverEnvironment(view, baseline);

            } catch (ClearToolError e) {
                e.printStackTrace(listener.getLogger());
                build.setResult(Result.FAILURE);
                /* return null to abort build */
                return null;
            }
        }
        /* return an empty environment (no deliver if not UCM tooled up SCM) */
        return new DeliverEnvironment();
    }

    private static ClearTool createCleartool(AbstractBuild<?, ?> build, BuildListener listener,
            File ctLogFile) throws IOException, InterruptedException
    {
        ClearCaseUcmSCM scm = (ClearCaseUcmSCM) build.getProject().getScm();
        EnvVars env = build.getEnvironment(listener);
        String nodeName = Computer.currentComputer().getName();
        ClearCaseConfiguration ccConfig = scm.fetchClearCaseConfig(nodeName);
        FilePath workspace;
        StringParameterValue wsParam = CCParametersAction.getBuildParameter(build,
                ORIGINAL_WORKSPACE_ENVSTR);
        if (wsParam != null) {
            workspace = new FilePath(build.getWorkspace().getChannel(), wsParam.value);
        } else {
            workspace = build.getWorkspace();
        }
        return scm.createClearTool(ccConfig.getCleartoolExe(), workspace, build.getBuiltOn()
                .getRootPath(), env, ctLogFile, null);
    }

    public class DeliverEnvironment extends BuildWrapper.Environment {

        private View deliverView;
        private Baseline deliveredBaseline;

        public DeliverEnvironment() {
            super();
            deliverView = null;
            deliveredBaseline = null;
        }

        public DeliverEnvironment(View deliverView, Baseline deliveredBaseline) {
            this.deliverView = deliverView;
            this.deliveredBaseline = deliveredBaseline;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException,
                InterruptedException
        {
            if (deliveredBaseline != null && deliverView != null) {
                try {
                    File ctLogFile = ClearToolLogFile.getCleartoolLogFile(build);
                    ClearCaseLogger logger = new ClearCaseLogger(listener, ctLogFile);
                    ClearTool ct = createCleartool(build, listener, ctLogFile);
                    if (Result.SUCCESS.equals(build.getResult())
                            || Result.UNSTABLE.equals(build.getResult())) {
                        logger.log("build result is " + build.getResult()
                                + ", completing deliver...");
                        ct.deliverComplete(deliveredBaseline.getStream(), deliverView);
                        logger.log("deliver successfully completed.");
                    } else {
                        logger.log("build result is " + build.getResult()
                                + ", cancelling deliver...");
                        ct.deliverCancel(deliveredBaseline.getStream(), deliverView);
                        logger.log("deliver successfully cancelled.");
                    }
                } catch (ClearToolError e) {
                    e.printStackTrace(listener.getLogger());
                    build.setResult(Result.FAILURE);
                    return false;
                }
            }
            return true;
        }

    }

    @Extension
    public static class Descriptor extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return item != null && item.getScm() instanceof ClearCaseUcmTooledUpSCM;
        }

        @Override
        public String getDisplayName() {
            return "[clearcase-thales] Integrate baselines from child streams";
        }

    }
}
