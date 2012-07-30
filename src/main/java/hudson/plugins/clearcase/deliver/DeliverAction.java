package hudson.plugins.clearcase.deliver;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.DeliverError;

import java.io.IOException;

public class DeliverAction {

    private ClearCaseLogger logger;
    private ClearTool ct;

    public DeliverAction(ClearCaseLogger logger, ClearTool ct) {
        this.logger = logger;
        this.ct = ct;
    }

    public void deliver(Baseline baseline, View view, AbstractBuild<?, ?> build)
            throws IOException, InterruptedException, ClearToolError
    {
        try {

            logger.log("Delivering baseline " + baseline + " to stream " + view.getStream() + "...");
            logger.log("Source stream: " + baseline.getStream());

            ct.deliver(baseline, view, true);

            logger.log("Deliver successful.");

        } catch (DeliverError e) {

            logger.log("Deliver error: " + e.getMessage());

            build.setResult(Result.FAILURE);
            build.setDescription(e.getMessage());

            ct.deliverCancel(baseline.getStream(), view);

            ct.changeBaselinePromotionLevel(baseline, PromotionLevel.REJECTED);

            throw e;
        }

    }
    
    

}
