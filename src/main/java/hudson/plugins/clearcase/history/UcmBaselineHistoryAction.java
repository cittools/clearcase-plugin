package hudson.plugins.clearcase.history;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.ClearCaseChangeLogSet;
import hudson.plugins.clearcase.changelog.UcmChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.cleartool.HistoryFormatHandler;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogSet.Entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UcmBaselineHistoryAction extends HistoryAction {

    private PromotionLevel baselineLevelThreshold;

    public UcmBaselineHistoryAction(ClearTool cleartool, PromotionLevel threshold) {
        super(cleartool);
        this.baselineLevelThreshold = threshold;
    }

    public UcmChangeLogSet getChanges(AbstractBuild<?, ?> build, Baseline baseline, View view)
            throws IOException, InterruptedException, ClearToolError
    {
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

    public Baseline findBaseline(View view) throws IOException, InterruptedException,
            ClearToolError
    {
        Baseline deliveredBaseline = null;
        for (Stream stream : cleartool.getChildStreams(view.getStream())) {
            List<Baseline> baselines = cleartool
                    .getBaselines(stream, baselineLevelThreshold, false);
            if (!baselines.isEmpty()) {
                deliveredBaseline = baselines.get(0);
                break;
            }
        }
        return deliveredBaseline;
    }

    @Override
    public boolean pollChanges(Date time, View view, List<String> branchNames,
            List<String> viewPaths) throws IOException, InterruptedException, ClearToolError
    {
        return findBaseline(view) != null;
    }

    @Override
    protected ClearCaseChangeLogSet<? extends Entry> buildChangelog(AbstractBuild<?, ?> build,
            View view, List<HistoryEntry> entries) throws IOException, InterruptedException
    {
        return null;
    }

    @Override
    public HistoryFormatHandler getHistoryFormatHandler() {
        return null;
    }

}
