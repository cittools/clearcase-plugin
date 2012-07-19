package hudson.plugins.clearcase;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.listeners.RunListener;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class ClearCaseUcmTooledUpSCM extends SCM {

    private String streamName;
    private String componentName;
    private Baseline.PromotionLevel promotionLevelThreshold;
    private String clearcaseConfig;
    private boolean createBaseline;
    private String baselinePattern;

    @Extension
    public static final ClearCaseUcmTooledUpSCMDescriptor DESCRIPTOR = new ClearCaseUcmTooledUpSCMDescriptor();

    @DataBoundConstructor
    public ClearCaseUcmTooledUpSCM(String streamName, String componentName,
            PromotionLevel promotionLevelThreshold, String clearcaseConfig)
    {
        super();
        this.streamName = streamName;
        this.componentName = componentName;
        this.promotionLevelThreshold = promotionLevelThreshold;
        this.clearcaseConfig = clearcaseConfig;
    }

    @Override
    public SCMDescriptor<ClearCaseUcmTooledUpSCM> getDescriptor() {
        return DESCRIPTOR;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild build, Launcher launcher,
            TaskListener listener) throws IOException, InterruptedException
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * 1. fetch existing baselines from child streams 2. if one baseline is
     * below the specified promotionLevelThreshold, trigger build
     */
    @SuppressWarnings("rawtypes")
    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher,
            FilePath workspace, TaskListener listener, SCMRevisionState baseline)
            throws IOException, InterruptedException
    {
        return null;
    }

    /**
     * 
     * <ol>
     * <li>create view</li>
     * <li>fetch existing baselines from child streams</li>
     * 
     * <li>if one baseline is below the specified promotionLevelThreshold
     * <ul>
     * <li>try to deliver baseline into integration stream</li>
     * <li>if deliver failed, cancel it & fail build</li>
     * </ul>
     * </li>
     * 
     * <li>launch build</li>
     * </ol>
     * 
     */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace,
            BuildListener listener, File changelogFile) throws IOException, InterruptedException
    {
        return false;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Extension
    public static class UcmTooledUpBuildListener extends RunListener<AbstractBuild> {

        public UcmTooledUpBuildListener() {
            super();
        }

        public UcmTooledUpBuildListener(Class<AbstractBuild> targetType) {
            super(targetType);
        }

        @Override
        public void onCompleted(AbstractBuild r, TaskListener listener) {
            super.onCompleted(r, listener);
        }
    }

}
