package hudson.plugins.clearcase;

import static hudson.Util.fixEmptyAndTrim;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.Launcher.ProcStarter;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.cleartool.CTLauncher;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.cleartool.ClearToolSnapshot;
import hudson.plugins.clearcase.objects.ClearCaseConfiguration;
import hudson.plugins.clearcase.util.Tools;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ClearCaseBaseSCMDescriptor extends SCMDescriptor<ClearCaseBaseSCM> {

    public static final String DEFAULT_CONFIG = "(Default)";
    
    
    /*******************************
     **** FIELDS *******************
     *******************************/
    
    @CopyOnWrite
    private volatile String cleartoolExe = "cleartool";
    @CopyOnWrite
    private volatile int changeLogMergeTimeWindow = 5;
    @CopyOnWrite
    private volatile String stgloc = "";
    @CopyOnWrite
    private volatile ClearCaseConfiguration[] configurations = new ClearCaseConfiguration[0];
      
    /*******************************
     **** CONSTRUCTOR **************
     *******************************/
    
    public ClearCaseBaseSCMDescriptor() {
        super(ClearCaseBaseSCM.class, null);
        load();
    }
    
    /*******************************
     **** OVERRIDE *****************
     *******************************/
    
    /** implements abstract method {@link Descriptor#getDisplayName()} */
    @Override
    public String getDisplayName() {
        return "Base ClearCase";
    }
    
    /** overrides method {@link Descriptor#configure()} */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            String exe = fixEmptyAndTrim(json.getString("cleartoolExe"));
            if (exe != null) {
                this.cleartoolExe = exe;
            } else {
                this.cleartoolExe = "cleartool";
            }
        } catch (JSONException jex) {
            this.cleartoolExe = "cleartool";
        }
        try {
            this.changeLogMergeTimeWindow = json.getInt("changeLogMergeTimeWindow");
        } catch (JSONException jex) {
            this.changeLogMergeTimeWindow = 5;
        }
        try {
            this.stgloc = json.getString("stgloc");
        } catch (JSONException jex) {
            this.stgloc = "";
        }
        try {
            this.configurations = req.bindParametersToList(
                    ClearCaseConfiguration.class,"cc.").toArray(new ClearCaseConfiguration[0]);
        } catch (JSONException jex) {
            this.configurations = new ClearCaseConfiguration[0];
        }
        save();
        return true;
    }
    
    /*******************************
     **** GETTERS ******************
     *******************************/
    
    public int getChangeLogMergeTimeWindow() {
        return changeLogMergeTimeWindow;
    }

    public String getCleartoolExe() {
        if (cleartoolExe == null) {
            cleartoolExe = "cleartool";
        }
        return cleartoolExe;
    }
    
    public String getStgloc() {
        return stgloc;
    }
    
    public ClearCaseConfiguration[] getConfigurations() {
        return configurations;
    }

    public ClearCaseConfiguration getConfiguration(String name) {
        if (name != null) {
            for (ClearCaseConfiguration conf : configurations) {
                if (name.equalsIgnoreCase(conf.getName())) {
                    return conf;
                }
            }
        }
        return new ClearCaseConfiguration(DEFAULT_CONFIG, cleartoolExe, stgloc, changeLogMergeTimeWindow);
    }
    
    /*******************************
     **** AJAX FIELD VALIDATORS ****
     *******************************/

    /** Checks if cleartool executable exists. */
    public FormValidation doCheckCleartoolExe(@QueryParameter String value) {
        String exe = fixEmptyAndTrim(value);
        return FormValidation.validateExecutable(exe);
    }
    
    /** Checks if the storage location exists */
    public FormValidation doCheckStgloc(@QueryParameter String value) {
        String stgloc = fixEmptyAndTrim(value);
        
        if (stgloc == null) {
            return FormValidation.ok();
        }
        
        Launcher launcher = Hudson.getInstance().createLauncher(TaskListener.NULL);

        FilePath hudsonRoot = Hudson.getInstance().getRootPath();
        
        CTLauncher ctLauncher = new CTLauncher(getCleartoolExe(), hudsonRoot, hudsonRoot, launcher,
                new EnvVars(), null);

        ClearTool ct = new ClearToolSnapshot(ctLauncher);

        try {
            List<String> stglocations = ct.lsStgloc();
            if (!stglocations.contains(stgloc)) {
                return FormValidation.error("Storage location not found. " +
                		"Please provide one from the following list : " + stglocations);
            } else {
                return FormValidation.ok();
            }
        } catch (Exception e) {
            return FormValidation.ok();
        }
        
    }
    
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

    /** Validates the config spec */
    public FormValidation doCheckConfigSpec(@QueryParameter String value) {
        
        String v = fixEmptyAndTrim(value);
        if ((v == null) || (v.length() == 0)) {
            return FormValidation.error("Config Spec is mandatory");
        }
        for (String cSpecLine : v.split("[\\r\\n]+")) {
            if (cSpecLine.startsWith("load ")) {
                return FormValidation.error("Config Spec can not contain load rules");
            }
        }
        return FormValidation.ok();
    }

    /** Validates the clearcase configuration name */
    public FormValidation doCheckName(@QueryParameter String value) {
        if (fixEmptyAndTrim(value) == null) {
            return FormValidation.error("Name is mandatory");
        } else {
            return FormValidation.ok();
        }
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
    
    /** Displays "cleartool version" for trouble shooting. */
    public void doVersion(StaplerRequest req, StaplerResponse rsp) throws IOException,
            ServletException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ProcStarter starter = Hudson.getInstance().createLauncher(TaskListener.NULL).launch();
            starter.cmds(new String[] { this.getCleartoolExe(), "-version" });
            starter.envs(new String[0]);
            starter.stdout(baos);
            starter.pwd(Hudson.getInstance().getRootPath());
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(starter);
            proc.join();
            rsp.setContentType("text/plain");
            baos.writeTo(rsp.getOutputStream());
        } catch (IOException e) {
            req.setAttribute("error", e);
            rsp.forward(this, "versionCheckError", req);
        }
    }
    
    public void doListViews(StaplerRequest req, StaplerResponse rsp) throws IOException,
            ServletException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProcStarter starter = Hudson.getInstance().createLauncher(TaskListener.NULL).launch();
        starter.cmds(new String[] { this.getCleartoolExe(), "lsview", "-short" });
        starter.envs(new String[0]);
        starter.stdout(baos);
        starter.pwd(Hudson.getInstance().getRootPath());
        Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(starter);
        proc.join();
        rsp.setContentType("text/plain");
        rsp.getOutputStream().println("ClearCase Views found:\n");
        baos.writeTo(rsp.getOutputStream());
    }
}