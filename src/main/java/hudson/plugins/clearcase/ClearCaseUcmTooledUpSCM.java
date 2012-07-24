package hudson.plugins.clearcase;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.clearcase.deliver.BaselineDeliverWrapper;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.scm.SCMDescriptor;
import hudson.tasks.BuildWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

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
    public boolean checkout(AbstractBuild build, Launcher l, FilePath workspace,
            BuildListener listener, File changelogFile) throws IOException, InterruptedException
    {
        
        
        /* 
         * */
        
        if (build.getProject() instanceof FreeStyleProject) {
            FreeStyleProject project = (FreeStyleProject) build.getProject();
            boolean deliverWrapperRegistered = false;

            Iterator<BuildWrapper> iterator = project.getBuildWrappersList().iterator();
            while (iterator.hasNext()) {
                if (iterator.next() instanceof BaselineDeliverWrapper) {
                    deliverWrapperRegistered = true;
                }
            }

            if (!deliverWrapperRegistered) {
                project.getBuildWrappersList().add(new BaselineDeliverWrapper());
            }
        }

        return super.checkout(build, l, workspace, listener, changelogFile);
    }

}
