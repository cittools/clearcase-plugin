package hudson.plugins.clearcase;

import static hudson.Util.fixEmptyAndTrim;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.util.Tools;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kohsuke.stapler.QueryParameter;

public class ClearCaseUcmSCMDescriptor extends SCMDescriptor<ClearCaseUcmSCM> {

    /*******************************
     **** CONSTRUCTOR **************
     *******************************/
    
    public ClearCaseUcmSCMDescriptor() {
        super(ClearCaseUcmSCM.class, null);
//        load();
    }

    /*******************************
     **** OVERRIDE *****************
     *******************************/
    
    /** implements abstract method {@link Descriptor#getDisplayName()} */
    @Override
    public String getDisplayName() {
        return "UCM ClearCase";
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
    public FormValidation doCheckViewName(@QueryParameter String value) {
        String viewName = fixEmptyAndTrim(value);
        if (viewName == null) {
            return FormValidation.error("View Name is mandatory");
        } else if (viewName.matches(Tools.ILLEGAL_CHARS_VIEW_REX)) {
            return FormValidation.error("Illegal characters");
        } else {
            return FormValidation.ok();
        }
    }
    
    /** Validates the view name */
    public FormValidation doCheckStream(@QueryParameter String value) {
        String stream = fixEmptyAndTrim(value);
        if (stream == null) {
            return FormValidation.error("Stream Selector is mandatory");
        } else if (stream.matches(Tools.ILLEGAL_CHARS_STREAM_REX)) {
            return FormValidation.error("Illegal characters");
        } else if (!stream.contains("@")) {
            return FormValidation.error("Missing VOB tag");
        } else {
            return FormValidation.warning("If you use an existing view, make sure " +
            		"to input the correct stream. If not, the view will be re-created.");
        }
    }
   
    /** Validates the load rules */
    public FormValidation doCheckLoadRules(@QueryParameter String value) {
        if (fixEmptyAndTrim(value) == null) {
            return FormValidation.error("Load Rules are mandatory (except for dynamic views)");
        } else {
            return FormValidation.ok();
        }
    }

    /** Validates the excludedRegions Regex */
    public FormValidation doCheckExcludedRegions(@QueryParameter String value) {
        
        String v = fixEmptyAndTrim(value);
        if (v != null) {
            String[] regions = v.split("[\\r\\n]+");
            for (String region : regions) {
                try {
                    Pattern.compile(region);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Invalid regular expression: " + region);
                }
            }
        }
        return FormValidation.ok();
    }
    
    public FormValidation doCheckUseUpdate(@QueryParameter String value) {
        if ("true".equals(fixEmptyAndTrim(value))) {
            return FormValidation.ok();
        } else {
            return FormValidation.warning("If the view already exists, it will be deleted and re-created.");
        }
    }

        
    /*******************************
     **** UTILITY METHODS **********
     *******************************/
    
    public String getDefaultViewPattern() {
        if (Functions.isWindows()) {
            return "${COMPUTERNAME}_${JOB_NAME}_hudson";
        } else {
            return "${HOSTNAME}_${JOB_NAME}_hudson";
        }
    }
    
    public String getDefaultViewRoot() {
        if (Functions.isWindows()) {
            return "M:\\";
        } else {
            return "/view";
        }
    }
}
