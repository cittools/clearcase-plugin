package hudson.plugins.clearcase.util;

import hudson.FilePath;

public class ClearToolError extends Exception {

    public static final String COMMAND_PREFIX = "Command Line >>> ";
    
    /////////// FIELDS /////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 730139672393755517L;
    private String commandLine;
    private String result;
    private int code = -1;
    private FilePath workdir;
    
    /////////// CONSTRUCTORS ///////////////////////////////////////////////////////////
    
    public ClearToolError(String commandLine, String result, int code, FilePath workdir) {
        super();
        this.commandLine = commandLine;
        this.result = result;
        this.code = code;
        this.workdir = workdir;
    }

    public ClearToolError(String message, String commandLine, String result, int code, FilePath workdir) {
        super(message);
        this.commandLine = commandLine;
        this.result = result;
        this.code = code;
        this.workdir = workdir;
    }

    public ClearToolError(Throwable cause, String commandLine, String result, int code, FilePath workdir) {
        super(cause);
        this.commandLine = commandLine;
        this.result = result;
        this.code = code;
        this.workdir = workdir;
    }

    public ClearToolError(String message, Throwable cause, String commandLine, String result, int code, FilePath workdir) {
        super(message, cause);
        this.commandLine = commandLine;
        this.result = result;
        this.code = code;
        this.workdir = workdir;
    }

    public ClearToolError() {
        super();
    }

    public ClearToolError(String message, Throwable cause) {
        super(message, cause);
    }

    public ClearToolError(String message) {
        super(message);
    }

    public ClearToolError(Throwable cause) {
        super(cause);
    }

    /////////// GETTERS //////////////////////////////////////////////////////////////// 
    
    public String getCommandLine() {
        return commandLine;
    }

    public String getResult() {
        return result;
    }
    
    public int getCode() {
        return code;
    }
    
    public FilePath getWorkdir() {
        return this.workdir;
    }
    
    public String getDirName() {
        if (this.workdir != null) {
            return "[" + this.workdir.getRemote().replaceFirst("^.+[/\\\\]", "") + "] $ ";
        } else {
            return "";
        }
    }
    
  
    ////////////////////////////////////////////////////////////////////////////////////
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("cleartool error: ");
        if (this.getMessage() != null) {
            sb.append(this.getMessage());
        }
        if (this.commandLine != null) {
            sb.append("\n" + COMMAND_PREFIX + getDirName() + this.commandLine);
        }
        if (this.result != null) {
            sb.append("\n" + this.result);
        }
        return sb.toString();
    }
    
}
