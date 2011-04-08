package hudson.plugins.clearcase.cleartool;
/*
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Launcher.ProcStarter;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { Launcher.class })
public class CTLauncherTest {

    @Test
    public void runTest() throws Exception {
        File logFile = File.createTempFile("cleartool", ".log");
        try {
            LauncherWrapper wrapper = PowerMockito.mock(LauncherWrapper.class);
            Proc p = mock(Proc.class);
            
            ProcStarter ps = new Launcher.LocalLauncher(TaskListener.NULL).new ProcStarter();
            when(p.join()).thenReturn(0);
            when(wrapper.launch()).thenReturn(ps);
            when(wrapper.launch(any(ProcStarter.class))).thenReturn(p);

            String executable = "cleartool";
            TaskListener listener = TaskListener.NULL;
            FilePath workspace = new FilePath(new File("."));
            EnvVars env = new EnvVars();

            CTLauncher ctLauncher = new CTLauncher(executable, listener, workspace, wrapper, env,
                    logFile);

            assertEquals(executable, ctLauncher.getExecutable());
            assertEquals(listener, ctLauncher.getListener());
            assertEquals(workspace, ctLauncher.getWorkspace());
            assertEquals(env, ctLauncher.getEnv());
            assertEquals(logFile, ctLauncher.getLogFile());
            assertEquals(wrapper, ctLauncher.getLauncher());

            ArgumentListBuilder args = new ArgumentListBuilder("lsvtree", "-l", "file1");

            assertEquals("", ctLauncher.run(args, null));
            
            
            FileInputStream fis = new FileInputStream(logFile);
            StringBuffer buff = new StringBuffer();
            int c;
            while ((c = fis.read()) != -1) {
                buff.append((char) c);
            }
            assertEquals(">>> cleartool lsvtree -l file1\n\n\n", buff.toString());
        } finally {
            logFile.delete();
        }
    }
}
*/