package hudson.plugins.clearcase.deliver;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.clearcase.ClearCaseUcmTooledUpSCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;

public class BaselineDeliverWrapper extends BuildWrapper {

    public BaselineDeliverWrapper() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException
    {
        return super.setUp(build, launcher, listener);
    }

    public class Environment extends BuildWrapper.Environment {

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException,
                InterruptedException
        {
            // TODO Auto-generated method stub
            return super.tearDown(build, listener);
        }

    }

    @Extension
    public static class Descriptor extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return item != null && item.getScm() instanceof ClearCaseUcmTooledUpSCM;
        }

        @Override
        public String getDisplayName() {
            return "[clearcase-thales] Integrate baselines from child streams";
        }

    }
}
