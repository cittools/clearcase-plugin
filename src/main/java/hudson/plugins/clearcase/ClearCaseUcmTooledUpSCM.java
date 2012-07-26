package hudson.plugins.clearcase;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.clearcase.changelog.ClearCaseChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.deliver.BaselineDeliverWrapper;
import hudson.plugins.clearcase.history.HistoryAction;
import hudson.plugins.clearcase.history.UcmBaselineHistoryAction;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildWrapper;

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

    private final String componentName;
    private final PromotionLevel baselineLevelThreshold;
    
    // ##########################
    // TRANSIENT
    // ##########################


    // ##########################
    // CONSTRUCTOR
    // ##########################

    public ClearCaseUcmTooledUpSCM(String viewName, boolean useUpdate, String loadRules,
            String clearcaseConfig, String customWorkspace, String streamName,
            String componentName, PromotionLevel baselineLevelThreshold)
    {
        super(viewName, "", false, useUpdate, "", loadRules, false, null, 0, clearcaseConfig,
                false, customWorkspace, streamName);
        this.componentName = componentName;
        this.baselineLevelThreshold = baselineLevelThreshold;
    }

    // ##########################
    // GETTERS
    // ##########################

    public String getComponentName() {
        return componentName;
    }

    public PromotionLevel getBaselineLevelThreshold() {
        return baselineLevelThreshold;
    }

    // ##########################
    // MAIN PROCESS
    // ##########################
    @Override
    public ChangeLogParser createChangeLogParser() {
        // TODO Auto-generated method stub
        return super.createChangeLogParser();
    }


    @Override
    protected HistoryAction createHistoryAction(ClearTool ct) {
        return null;
    }

    @Override
    protected boolean canGatherChangelog(ClearTool cleartool) {
        return true;
    }

    @Override
    protected ClearCaseChangeLogSet<? extends Entry> gatherChangelog(AbstractBuild<?, ?> build,
            ClearCaseLogger logger, View view, ClearTool cleartool) throws IOException,
            InterruptedException, ClearToolError
    {
        for (Stream stream : cleartool.getChildStreams(view.getStream()))  {
            List<Baseline> baselines = cleartool.getBaselines(stream, baselineLevelThreshold);
            if (!baselines.isEmpty()) {
                
            }
        }
        
        FreeStyleBuild b = (FreeStyleBuild) build;
        b.getEnvironments().add(new BaselineDeliverWrapper.DeliverEnvironment(view, deliveredBaseline));
        
        
        UcmBaselineHistoryAction historyAction = new UcmBaselineHistoryAction(cleartool);
        
        
        return super.gatherChangelog(build, logger, view, cleartool);
    }

}
