/**
 * The MIT License
 *
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.clearcase.cleartool;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.util.ArgumentListBuilder;
import hudson.util.ForkOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.output.NullOutputStream;

public class CTLauncher {

    /*******************************
     **** FIELDS *******************
     *******************************/

    private final FilePath workspace;
    private final FilePath nodeRoot;
    private final Launcher launcher;
    private final EnvVars env;
    private final File logFile;

    private final String executable;

    /*******************************
     **** CONSTRUCTOR **************
     *******************************/

    public CTLauncher(String executable, FilePath workspace, FilePath nodeRoot,
            EnvVars env, File logFile, Launcher launcher)
    {
        this.executable = executable;
        this.workspace = workspace;
        this.nodeRoot = nodeRoot;
        this.env = env;
        this.logFile = logFile;
        this.launcher = launcher;
    }

    /*******************************
     **** METHODS ******************
     *******************************/

    /**
     * Run a clearcase command without output to the console, the result of the
     * command is returned as a String
     * 
     * @param cmd
     *            the command to launch using the clear tool executable
     * @param filePath
     *            optional, the path where the command should be launched
     * @return the result of the command
     * @throws IOException
     * @throws InterruptedException
     */
    public String run(ArgumentListBuilder args, FilePath filePath) throws IOException,
            InterruptedException, ClearToolError
    {
        FilePath path = filePath;
        if (path == null) {
            path = this.nodeRoot;
        }
        ArgumentListBuilder cmd = new ArgumentListBuilder(this.executable);
        cmd.add(args.toCommandArray());

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream logStream;
        
        
        if (logFile == null) {
            logStream = new DataOutputStream(new NullOutputStream()); 
        } else {
            logStream = new DataOutputStream(new FileOutputStream(logFile, true /*append*/));
        }
        
        ForkOutputStream forkStream = new ForkOutputStream(outStream, logStream);
        
        int code;
        String cleartoolResult;

        try {
            logStream.writeBytes(">>> " + cmd.toStringWithQuote() + "\n");
            
            ProcStarter starter = launcher.launch();
            starter.cmds(cmd);
            starter.envs(this.env);
            starter.stdout(forkStream);
            starter.pwd(path);

            code = launcher.launch(starter).join();

            cleartoolResult = outStream.toString();
            logStream.writeBytes("\n\n"); // to separate the commands
        } finally {
            forkStream.close();
        }

        if (cleartoolResult.contains("cleartool: Error") || code != 0) {
            throw new ClearToolError(cmd.toStringWithQuote(), cleartoolResult, code, path);
        }

        return cleartoolResult;
    }

    /*******************************
     **** GETTERS ******************
     *******************************/

    public FilePath getWorkspace()
    {
        return workspace;
    }
    
    public FilePath getNodeRoot() {
        return nodeRoot;
    }

    public Launcher getLauncher()
    {
        return this.launcher;
    }

    public String getExecutable()
    {
        return executable;
    }

    public EnvVars getEnv() {
        return this.env;
    }
    
    public File getLogFile() {
        return logFile;
    }
}
