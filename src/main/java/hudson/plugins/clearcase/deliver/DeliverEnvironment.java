package hudson.plugins.clearcase.deliver;

import static hudson.plugins.clearcase.AbstractClearCaseSCM.ORIGINAL_WORKSPACE_ENVSTR;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Environment;
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
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.CCParametersAction;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.File;
import java.io.IOException;



public class DeliverEnvironment extends Environment {

    private View deliverView;
    private Baseline deliveredBaseline;
    // If the build is successfull tearDown is called without setting the build result...
    private static final Result SUCCESS = null;

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
                if (build.getResult() == SUCCESS) {
                    logger.log("build result is " + Result.SUCCESS + ", completing deliver...");
                    ct.deliverComplete(deliveredBaseline.getStream(), deliverView);
                    ct.changeBaselinePromotionLevel(deliveredBaseline, PromotionLevel.BUILT);
                    build.setDescription("<small>baseline delivered: <b>" + deliveredBaseline + "</b></small>");
                    logger.log("deliver successfully completed.");
                } else {
                    logger.log("build result is " + build.getResult()
                            + ", cancelling deliver...");
                    ct.deliverCancel(deliveredBaseline.getStream(), deliverView);
                    ct.changeBaselinePromotionLevel(deliveredBaseline, PromotionLevel.REJECTED);
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
    
    private ClearTool createCleartool(AbstractBuild<?, ?> build, BuildListener listener,
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

}
