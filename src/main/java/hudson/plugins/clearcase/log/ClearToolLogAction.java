package hudson.plugins.clearcase.log;

import hudson.model.Action;

import org.kohsuke.stapler.StaplerProxy;

public class ClearToolLogAction implements Action, StaplerProxy {

    /*******************************
     **** CONSTRUCTOR **************
     *******************************/
    public ClearToolLogAction() {
    }
    
    /*******************************
     **** OVERRIDE *****************
     *******************************/
    @Override
    public String getDisplayName() {
        return "ClearTool Output";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/clearcase-thales/icons/terminal-24x24.gif";
    }

    @Override
    public String getUrlName() {
        return "cleartooloutput";
    }

    @Override
    public Object getTarget() {
        return new ClearToolLogFile();
    }
        
}
