package hudson.plugins.clearcase.history;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.UcmChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UcmBaselineHistoryAction {
    
    private ClearTool cleartool;

    public UcmBaselineHistoryAction(ClearTool cleartool) {
        this.cleartool = cleartool;
    }

    public UcmChangeLogSet getChanges(AbstractBuild<?, ?> build, Baseline baseline, View view) throws IOException, InterruptedException, ClearToolError {
        
        UcmChangeLogSet changeLog = null;
               
        List<String> baselineActivities = cleartool.getBaselineActivities(baseline);
        
        if (!baselineActivities.isEmpty()) {
            List<UcmActivity> activities = new ArrayList<UcmActivity>();
            for (String activityName : baselineActivities) {
                activities.add(cleartool.lsactivityFull(activityName, view));
            }
            changeLog = new UcmChangeLogSet(build, activities);
        }
        
        return changeLog;
    }

}
