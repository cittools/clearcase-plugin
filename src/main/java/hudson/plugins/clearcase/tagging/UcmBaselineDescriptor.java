package hudson.plugins.clearcase.tagging;

import static hudson.Util.fixEmptyAndTrim;
import hudson.model.AbstractProject;
import hudson.plugins.clearcase.AbstractClearCaseSCM;
import hudson.plugins.clearcase.ClearCaseBaseSCM;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.util.Tools;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import org.kohsuke.stapler.QueryParameter;


public class UcmBaselineDescriptor extends BuildStepDescriptor<Publisher> {

    public static final String DEFAULT_NAME_PATTERN = "${JOB_NAME}_${BUILD_ID}_hudson";
    public static final String DEFAULT_COMMENT_PATTERN = "Created automatically by Hudson";
    
    /*******************************
     **** CONSTRUCTOR **************
     *******************************/
    
    public UcmBaselineDescriptor() {
        super(UcmBaseline.class);
    }
    
    /*******************************
     **** OVERRIDE *****************
     *******************************/
    
    @Override
    public String getDisplayName() {
        return String.format("[%s] Make UCM baseline", AbstractClearCaseSCM.PLUGIN_NAME);
    }
 
    @Override
    @SuppressWarnings("rawtypes")
    public boolean isApplicable(Class<? extends AbstractProject> clazz) {
        return true;
    }
    
    /*******************************
     **** GETTERS ******************
     *******************************/
    
    public ClearCaseConfiguration[] getConfigurations() {
        return ClearCaseBaseSCM.BASE_DESCRIPTOR.getConfigurations();
    }

    public ClearCaseConfiguration getConfiguration(String name) {
        return ClearCaseBaseSCM.BASE_DESCRIPTOR.getConfiguration(name);
    }
    
    /*******************************
     **** AJAX FIELD VALIDATORS ****
     *******************************/
    
    /** Validates the view name */
    public FormValidation doCheckNamePattern(@QueryParameter String value) {
        String blName = fixEmptyAndTrim(value);
        if (blName == null) {
            return FormValidation.error("Name Pattern is mandatory");
        } else if (blName.matches(Tools.ILLEGAL_CHARS_BL_REX)) {
            return FormValidation.error("Illegal characters");
        } else {
            return FormValidation.ok();
        }
    }
}
