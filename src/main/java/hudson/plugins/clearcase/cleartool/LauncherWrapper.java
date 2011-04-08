package hudson.plugins.clearcase.cleartool;

import hudson.CloseProofOutputStream;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.ProcStarter;
import hudson.Launcher.RemoteLauncher;
import hudson.Proc.LocalProc;
import hudson.Proc.RemoteProc;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This is an override of hudson's Launcher
 * 
 * There was no way of preventing Hudson to print the command line.
 * 
 * @author Robin Jarry
 * 
 */
public class LauncherWrapper {

    Launcher launcher;

    public LauncherWrapper(Launcher launcher) {
        if (launcher instanceof LocalLauncher) {
            this.launcher = new LocalLauncherWrapper(launcher.getListener());
        } else if (launcher instanceof RemoteLauncher) {
            this.launcher = new RemoteLauncherWrapper(launcher.getListener(), launcher.getChannel(),
                    launcher.isUnix());
        }
    }

    public Proc launch(ProcStarter starter) throws IOException {
        return this.launcher.launch(starter);
    }
    
    public ProcStarter launch() {
        return this.launcher.launch();
    }

    /////////////////////////////////////////
    //////////// LOCAL LAUNCHER /////////////
    /////////////////////////////////////////
    static class LocalLauncherWrapper extends LocalLauncher {

        public LocalLauncherWrapper(TaskListener listener) {
            this(listener,Hudson.MasterComputer.localChannel);
        }

        public LocalLauncherWrapper(TaskListener listener, VirtualChannel channel) {
            super(listener, channel);
        }

        @Override
        public Proc launch(ProcStarter ps) throws IOException {
            EnvVars jobEnv = inherit(ps.envs());

            // replace variables in command line
            String[] jobCmd = new String[ps.cmds().size()];
            for ( int idx = 0 ; idx < jobCmd.length; idx++ )
                jobCmd[idx] = jobEnv.expand(ps.cmds().get(idx));

            return new LocalProc(jobCmd, Util.mapToEnv(jobEnv), ps.stdin(), ps.stdout(), ps.stderr(), toFile(ps.pwd()));
        }

        private File toFile(FilePath f) {
            return f==null ? null : new File(f.getRemote());
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String,String> envVars) throws IOException {
            //printCommandLine(cmd, workDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(toFile(workDir));
            if (envVars!=null) pb.environment().putAll(envVars);

            return launchChannel(out, pb);
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) {
            ProcessTree.get().killAll(modelEnvVars);
        }

        /**
         * @param out
         *      Where the stderr from the launched process will be sent.
         */
        @Override
        public Channel launchChannel(OutputStream out, ProcessBuilder pb) throws IOException {
            final EnvVars cookie = EnvVars.createCookie();
            pb.environment().putAll(cookie);

            final Process proc = pb.start();

            final Thread t2 = new StreamCopyThread(pb.command()+": stderr copier", proc.getErrorStream(), out);
            t2.start();

            return new Channel("locally launched channel on "+ pb.command(),
                Computer.threadPoolForRemoting, proc.getInputStream(), proc.getOutputStream(), out) {

                /**
                 * Kill the process when the channel is severed.
                 */
                @Override
                protected synchronized void terminate(IOException e) {
                    super.terminate(e);
                    ProcessTree pt = ProcessTree.get();
                    pt.killAll(proc,cookie);
                }

                @Override
                public synchronized void close() throws IOException {
                    super.close();
                    // wait for all the output from the process to be picked up
                    try {
                        t2.join();
                    } catch (InterruptedException e) {
                        // process the interrupt later
                        Thread.currentThread().interrupt();
                    }
                }
            };
        }
    }
    //////////////////////////////////////////
    //////////// REMOTE LAUNCHER /////////////
    //////////////////////////////////////////
    static class RemoteLauncherWrapper extends RemoteLauncher {

        public RemoteLauncherWrapper(TaskListener listener, VirtualChannel channel, boolean isUnix) {
            super(listener, channel, isUnix);
        }

        @Override
        public Proc launch(ProcStarter ps) throws IOException {
            final OutputStream out = ps.stdout() == null ? null : new RemoteOutputStream(new CloseProofOutputStream(ps.stdout()));
            final OutputStream err = ps.stderr()==null ? null : new RemoteOutputStream(new CloseProofOutputStream(ps.stderr()));
            final InputStream  in  = ps.stdin()==null ? null : new RemoteInputStream(ps.stdin());
            final String workDir = ps.pwd()==null ? null : ps.pwd().getRemote();

            return new RemoteProc(getChannel().callAsync(new RemoteLaunchCallable(ps.cmds(), ps.envs(), in, out, err, workDir, listener)));
        }
        @Override
        public Channel launchChannel(String[] cmd, OutputStream err, FilePath _workDir, Map<String,String> envOverrides) throws IOException, InterruptedException {
            Pipe out = Pipe.createRemoteToLocal();
            final String workDir = _workDir==null ? null : _workDir.getRemote();

            OutputStream os = getChannel().call(new RemoteChannelLaunchCallable(cmd, out, err, workDir, envOverrides));

            return new Channel("remotely launched channel on "+channel,
                Computer.threadPoolForRemoting, out.getIn(), new BufferedOutputStream(os));
        }

        @Override
        public boolean isUnix() {
            return super.isUnix();
        }

        @Override
        public void kill(final Map<String,String> modelEnvVars) throws IOException, InterruptedException {
            getChannel().call(new KillTask(modelEnvVars));
        }

        private static final class KillTask implements Callable<Void,RuntimeException> {
            private final Map<String, String> modelEnvVars;

            public KillTask(Map<String, String> modelEnvVars) {
                this.modelEnvVars = modelEnvVars;
            }

            public Void call() throws RuntimeException {
                ProcessTree.get().killAll(modelEnvVars);
                return null;
            }

            private static final long serialVersionUID = 1L;
        }
    }

    
    
    
    
    
    
    private static class RemoteLaunchCallable implements Callable<Integer,IOException> {
        private final List<String> cmd;
        private final String[] env;
        private final InputStream in;
        private final OutputStream out;
        private final OutputStream err;
        private final String workDir;
        private final TaskListener listener;

        RemoteLaunchCallable(List<String> cmd, String[] env, InputStream in, OutputStream out, OutputStream err, String workDir, TaskListener listener) {
            this.cmd = new ArrayList<String>(cmd);
            this.env = env;
            this.in = in;
            this.out = out;
            this.err = err;
            this.workDir = workDir;
            this.listener = listener;
        }

        public Integer call() throws IOException {
            Launcher.ProcStarter ps = new LocalLauncher(listener).launch();
            ps.cmds(cmd).envs(env).stdin(in).stdout(out).stderr(err);
            if(workDir!=null)   ps.pwd(workDir);

            Proc p = ps.start();
            try {
                return p.join();
            } catch (InterruptedException e) {
                return -1;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static class RemoteChannelLaunchCallable implements Callable<OutputStream,IOException> {
        private final String[] cmd;
        private final Pipe out;
        private final String workDir;
        private final OutputStream err;
        private final Map<String,String> envOverrides;

        public RemoteChannelLaunchCallable(String[] cmd, Pipe out, OutputStream err, String workDir, Map<String,String> envOverrides) {
            this.cmd = cmd;
            this.out = out;
            this.err = new RemoteOutputStream(err);
            this.workDir = workDir;
            this.envOverrides = envOverrides;
        }

        public OutputStream call() throws IOException {
            Process p = Runtime.getRuntime().exec(cmd,
                Util.mapToEnv(inherit(envOverrides)),
                workDir == null ? null : new File(workDir));

            List<String> cmdLines = Arrays.asList(cmd);
            new StreamCopyThread("stdin copier for remote agent on "+cmdLines,
                p.getInputStream(), out.getOut()).start();
            new StreamCopyThread("stderr copier for remote agent on "+cmdLines,
                p.getErrorStream(), err).start();

            return new RemoteOutputStream(p.getOutputStream());
        }

        private static final long serialVersionUID = 1L;
    }
    
    
    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(String[] env) {
        // convert String[] to Map first
        EnvVars m = new EnvVars();
        if(env!=null) {
            for (String e : env) {
                int index = e.indexOf('=');
                m.put(e.substring(0,index), e.substring(index+1));
            }
        }
        // then do the inheritance
        return inherit(m);
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(Map<String,String> overrides) {
        EnvVars m = new EnvVars(EnvVars.masterEnvVars);
        for (Map.Entry<String,String> o : overrides.entrySet()) 
            m.override(o.getKey(),m.expand(o.getValue()));
        return m;
    }
}
