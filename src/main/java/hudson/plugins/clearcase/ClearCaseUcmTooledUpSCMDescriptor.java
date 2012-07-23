package hudson.plugins.clearcase;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.scm.SCMDescriptor;

public class ClearCaseUcmTooledUpSCMDescriptor extends SCMDescriptor<ClearCaseUcmTooledUpSCM> {

    public static final PromotionLevel[] PROMOTION_LEVELS = {
        PromotionLevel.INITIAL,
        PromotionLevel.BUILT, 
        PromotionLevel.TESTED, 
        PromotionLevel.RELEASED 
    };

    public ClearCaseUcmTooledUpSCMDescriptor() {
        super(ClearCaseUcmTooledUpSCM.class, null);
    }
    
    @Override
    public boolean isApplicable(AbstractProject project) {
        return project instanceof FreeStyleProject;
    }

    @Override
    public String getDisplayName() {
        return "[clearcase-thales] UCM Tooled-up Process";
    }

    public ClearCaseConfiguration[] getConfigurations() {
        return ClearCaseBaseSCM.BASE_DESCRIPTOR.getConfigurations();
    }

    public ClearCaseConfiguration getConfiguration(String name) {
        return ClearCaseBaseSCM.BASE_DESCRIPTOR.getConfiguration(name);
    }

    
    public PromotionLevel[] getPromotionLevels() {
        return PROMOTION_LEVELS;
    }

}