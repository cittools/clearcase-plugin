package hudson.plugins.clearcase.log;

import hudson.model.TaskListener;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class ClearCaseLogger {
    
    private TaskListener listener;
    private File cleartoolLogFile;
    
    public ClearCaseLogger(TaskListener listener, File cleartoolLogFile) {
        this.listener = listener;
        this.cleartoolLogFile = cleartoolLogFile;
    }

    /**
     * Log output to the given logger
     * 
     * @param listener
     *            The current listener
     * @param message
     *            The message to be outputted
     */
    public void log(String message) {
        listener.getLogger().println("[ClearCase] " + message);
        if (cleartoolLogFile != null && message != null && 
                !message.contains(ClearToolError.COMMAND_PREFIX)) {
            try {
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(cleartoolLogFile, true));
                dos.writeBytes("[ClearCase] " + message + "\n");
                dos.close();
            } catch (Exception e) {
                /* do nothing */
            }
        }
    }
}
