package hudson.plugins.clearcase.objects;

import org.kohsuke.stapler.DataBoundConstructor;


/**
 * Represents a global clearcase configuration that can be used in jobs.
 * 
 * @author Robin Jarry
 */
public final class ClearCaseConfiguration {

    private final String name;
    private final String cleartoolExe;
    private final String stgloc;
    private final int changeLogMergeTimeWindow;
    private final int ccCmdDelay;
    
    @DataBoundConstructor
    public ClearCaseConfiguration(String name, String cleartoolExe, String stgloc, int changeLogMergeTimeWindow, int ccCmdDelay) {
        this.name = name;
        this.cleartoolExe = cleartoolExe;
        this.stgloc = stgloc;
        this.changeLogMergeTimeWindow = changeLogMergeTimeWindow;
        this.ccCmdDelay = ccCmdDelay;
    }

    public String getName() {
        return name;
    }

    public String getCleartoolExe() {
        return cleartoolExe;
    }

    public String getStgloc() {
        return stgloc;
    }

    public int getChangeLogMergeTimeWindow() {
        return changeLogMergeTimeWindow;
    }
    
    public int getccCmdDelay() {
        return ccCmdDelay;
    }

}
