package hudson.plugins.clearcase;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.BaselineChangeLogParser;
import hudson.plugins.clearcase.changelog.UcmChangeLogParser;
import hudson.plugins.clearcase.changelog.UcmChangeLogSet;
import hudson.plugins.clearcase.checkout.CheckoutAction;
import hudson.plugins.clearcase.checkout.UcmDynamicCheckoutAction;
import hudson.plugins.clearcase.checkout.UcmSnapshotCheckoutAction;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.deliver.DeliverAction;
import hudson.plugins.clearcase.deliver.DeliverEnvironment;
import hudson.plugins.clearcase.history.HistoryAction;
import hudson.plugins.clearcase.history.UcmBaselineHistoryAction;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class ClearCaseUcmTooledUpSCM extends ClearCaseUcmSCM {

    @Extension
    public static final ClearCaseUcmTooledUpSCMDescriptor DESCRIPTOR = new ClearCaseUcmTooledUpSCMDescriptor();

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
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
        return new UcmBaselineHistoryAction(ct, baselineLevelThreshold);
    }

    @Override
    protected boolean canGatherChangelog(ClearTool cleartool) {
        return true;
    }
    
    /** implementation of abstract method {@link hudson.scm.SCM#createChangeLogParser()} */
    @Override
    public ChangeLogParser createChangeLogParser() {
        return new BaselineChangeLogParser();
    }

    @Override
    protected CheckoutAction createCheckoutAction(ClearTool ct, ClearCaseLogger logger, View view,
            String stgloc,int ccCmdDelay)
    {
        CheckoutAction action;
        if (isUseDynamicView()) {
            action = new UcmDynamicCheckoutAction(ct, logger, view, stgloc,
                    getMkviewOptionalParam(), isUseUpdate(), isDoNotUpdateConfigSpec(),
                    ClearCaseBaseSCM.BASE_DESCRIPTOR.getTimeShift(), /*freezeView*/ false);
        } else {
            action = new UcmSnapshotCheckoutAction(ct, logger, view, stgloc,
                    getMkviewOptionalParam(), isUseUpdate(), getViewPaths(ct.getWorkspace()),ccCmdDelay);
        }
        return action;
    }
    
    @Override
    protected UcmChangeLogSet gatherChangelog(AbstractBuild<?, ?> build, ClearCaseLogger logger,
            View view, ClearTool ct) throws IOException, InterruptedException, ClearToolError
    {

        UcmBaselineHistoryAction historyAction = new UcmBaselineHistoryAction(ct,
                baselineLevelThreshold);
        Baseline deliveredBaseline = historyAction.findBaseline(view);

        UcmChangeLogSet changes = null;
        if (deliveredBaseline != null) {
            logger.log("Delivering baseline " + deliveredBaseline + " to build view...");
            DeliverAction deliver = new DeliverAction(logger, ct);
            deliver.deliver(deliveredBaseline, view, build);

            try {
                logger.log("Gathering baseline changelog...");
                changes = historyAction.getChanges(build, deliveredBaseline, view);
            } catch (ClearToolError e) {
                logger.log("Error while gathering changelog, cancelling deliver...");
                ct.deliverCancel(deliveredBaseline.getStream(), view);
                throw e;
            }

            DeliverEnvironment deliverEnv = new DeliverEnvironment(view, deliveredBaseline);
            ((FreeStyleBuild) build).getEnvironments().add(deliverEnv);
        } else {
            logger.log("No baseline to deliver.");
        }

        return changes;
    }

}
