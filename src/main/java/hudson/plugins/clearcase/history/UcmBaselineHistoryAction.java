package hudson.plugins.clearcase.history;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.ClearCaseChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.View;
import hudson.scm.ChangeLogSet;

public class UcmBaselineHistoryAction {

    private ClearTool cleartool;

    public UcmBaselineHistoryAction(ClearTool cleartool) {
        this.cleartool = cleartool;
    }

    public ClearCaseChangeLogSet<? extends ChangeLogSet.Entry> getChanges(
            AbstractBuild<?, ?> build, Baseline baseline, View view)
    {
        return null;
    }

}
