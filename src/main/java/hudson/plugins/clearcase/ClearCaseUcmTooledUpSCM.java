package hudson.plugins.clearcase;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.UcmChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.deliver.DeliverAction;
import hudson.plugins.clearcase.deliver.DeliverEnvironment;
import hudson.plugins.clearcase.history.HistoryAction;
import hudson.plugins.clearcase.history.UcmBaselineHistoryAction;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.SCMDescriptor;

import java.io.IOException;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class ClearCaseUcmTooledUpSCM extends ClearCaseUcmSCM {

    @Extension
    public static final ClearCaseUcmTooledUpSCMDescriptor DESCRIPTOR = new ClearCaseUcmTooledUpSCMDescriptor();

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return ClearCaseUcmTooledUpSCM.DESCRIPTOR;
    }

    public static final String CLEARCASE_BUILT_BASELINE_ENVSTR = "CLEARCASE_DELIVERED_BASELINE";

    // ##########################
    // FIELDS
    // ##########################

    private final PromotionLevel baselineLevelThreshold;

    // ##########################
    // TRANSIENT
    // ##########################

    // ##########################
    // CONSTRUCTOR
    // ##########################
    @DataBoundConstructor
    public ClearCaseUcmTooledUpSCM(String viewName, String mkviewOptionalParam,
            boolean filteringOutDestroySubBranchEvent, boolean useUpdate, String excludedRegions,
            String loadRules, boolean useDynamicView, String viewDrive, int multiSitePollBuffer,
            String clearcaseConfig, boolean doNotUpdateConfigSpec, String customWorkspace,
            String stream, PromotionLevel baselineLevelThreshold)
    {
        super(viewName, mkviewOptionalParam, filteringOutDestroySubBranchEvent, useUpdate,
                excludedRegions, loadRules, useDynamicView, viewDrive, multiSitePollBuffer,
                clearcaseConfig, doNotUpdateConfigSpec, customWorkspace, stream);
        this.baselineLevelThreshold = baselineLevelThreshold;
    }

    // ##########################
    // GETTERS
    // ##########################

    public PromotionLevel getBaselineLevelThreshold() {
        return baselineLevelThreshold;
    }

    // ##########################
    // MAIN PROCESS
    // ##########################

    @Override
    protected HistoryAction createHistoryAction(ClearTool ct) {
        return null;
    }

    @Override
    protected boolean canGatherChangelog(ClearTool cleartool) {
        return true;
    }

    @Override
    protected UcmChangeLogSet gatherChangelog(AbstractBuild<?, ?> build, ClearCaseLogger logger,
            View view, ClearTool ct) throws IOException, InterruptedException, ClearToolError
    {
        Baseline deliveredBaseline = null;
        for (Stream stream : ct.getChildStreams(view.getStream())) {
            logger.log("Fetching baselines to deliver from stream " + stream + "...");
            List<Baseline> baselines = ct.getBaselines(stream, baselineLevelThreshold, false);
            if (!baselines.isEmpty()) {
                deliveredBaseline = baselines.get(0);
                break;
            }
        }

        UcmChangeLogSet changes = null;

        if (deliveredBaseline != null) {
            logger.log("Delivering baseline " + deliveredBaseline + " to build view...");
            DeliverAction deliver = new DeliverAction(logger, ct);
            deliver.deliver(deliveredBaseline, view, build);

            logger.log("Gathering baseline changelog...");
            UcmBaselineHistoryAction historyAction = new UcmBaselineHistoryAction(ct);
            changes = historyAction.getChanges(build, deliveredBaseline, view);

            DeliverEnvironment deliverEnv = new DeliverEnvironment(view, deliveredBaseline);
            ((FreeStyleBuild) build).getEnvironments().add(deliverEnv);
        } else {
            logger.log("No baseline to deliver.");
        }

        return changes;
    }

}
