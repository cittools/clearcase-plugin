package hudson.plugins.clearcase;

import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.plugins.clearcase.changelog.UcmChangeLogParser;
import hudson.plugins.clearcase.util.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { Functions.class, Tools.class })
public class ClearCaseUcmSCMTest {

    private static ClearCaseUcmSCM scm;

    @BeforeClass
    public static void setUp() {
        String viewName = "${USER} ${MACHINE} hudson";
        String mkviewOptionalParam = "-stgloc storage1";
        boolean filteringOutDestroySubBranchEvent = true;
        boolean useUpdate = false;
        String excludedRegions = ".svn";
        String loadRules = "/vobs/VOB1/compA\n/vobs/VOB2/compC\n/vobs/VOB3/compY/folder";
        boolean useDynamicView = false;
        String viewDrive = "/viewdrive/";
        int multiSitePollBuffer = 1524;
        String clearcaseConfig = "CC_config";
        String stream = "stream@/vobtag";
        boolean doNotUpdateConfigSpec = false;
        String customWorkspace = "";

        scm = new ClearCaseUcmSCM(viewName, mkviewOptionalParam, filteringOutDestroySubBranchEvent,
                useUpdate, excludedRegions, loadRules, useDynamicView, viewDrive,
                multiSitePollBuffer, clearcaseConfig, doNotUpdateConfigSpec,
                customWorkspace, stream);
    }

    @Test
    public void dataBoundConstructorTest() throws Exception {
        Assert.assertEquals("${USER} ${MACHINE} hudson", scm.getViewName());
        Assert.assertEquals("-stgloc storage1", scm.getMkviewOptionalParam());
        Assert.assertTrue(scm.isFilteringOutDestroySubBranchEvent());
        Assert.assertFalse(scm.isUseUpdate());
        Assert.assertEquals(".svn", scm.getExcludedRegions());
        Assert.assertEquals("/vobs/VOB1/compA\n/vobs/VOB2/compC\n/vobs/VOB3/compY/folder", scm
                .getLoadRules());
        Assert.assertFalse(scm.isUseDynamicView());
        Assert.assertEquals("/viewdrive/", scm.getViewDrive());
        Assert.assertEquals(1524, scm.getMultiSitePollBuffer());
        Assert.assertEquals("CC_config", scm.getClearcaseConfig());
        Assert.assertEquals("stream@/vobtag", scm.getStream());
        Assert.assertFalse(scm.isDoNotUpdateConfigSpec());
        Assert.assertNull(scm.getCustomWorkspace());
    }

    @Test
    public void viewAndDriveNullTest() throws Exception {
        ClearCaseUcmSCM scm = new ClearCaseUcmSCM(null, null, false, false, null, null, false,
                null, 0, null, false, "", "");

        PowerMockito.mockStatic(Functions.class);

        when(Functions.isWindows()).thenReturn(true);
        Assert.assertEquals("${COMPUTERNAME}_${JOB_NAME}_hudson", scm.getViewName());
        Assert.assertEquals("M:\\", scm.getViewDrive());

        when(Functions.isWindows()).thenReturn(false);
        Assert.assertEquals("${HOSTNAME}_${JOB_NAME}_hudson", scm.getViewName());
        Assert.assertEquals("/view", scm.getViewDrive());
    }

 
    @Test
    public void miscMethodsTest() throws Exception {
        Assert.assertTrue(scm.supportsPolling());
        Assert.assertTrue(scm.requiresWorkspaceForPolling());
        Assert.assertNotNull(scm.getDescriptor());
        Assert.assertTrue(scm.getDescriptor() instanceof ClearCaseUcmSCMDescriptor);
        Assert.assertNotNull(scm.createChangeLogParser());
        Assert.assertTrue(scm.createChangeLogParser() instanceof UcmChangeLogParser);

        scm.setEnv(new EnvVars());
        scm.getResolvedStreamName();

        List<String> branches = new ArrayList<String>();
        branches.add("stream");
        Assert.assertEquals(branches, scm.getBranchNames());
    }

    @Test
    public void getNormalizedViewNameTest() throws Exception {
        ClearCaseUcmSCM scm = new ClearCaseUcmSCM("${USER} ${MACHINE} hudson", null, false, false,
                null, null, false, null, 0, null, false, "", "");
        EnvVars env = new EnvVars("USER", "jacky", "MACHINE", "computer1");
        scm.setEnv(env);
        Assert.assertEquals("jacky_computer1_hudson", scm.getNormalizedViewName());

        ClearCaseUcmSCM scm2 = new ClearCaseUcmSCM("${USER} ${UNBOUND_VARIABLE}", null, false,
                false, null, null, false, null, 0, null, false, "", "");
        scm2.setEnv(env);
        Assert.assertEquals("jacky_${UNBOUND_VARIABLE}", scm2.getNormalizedViewName());
    }


    @Test
    public void publishEnvVarsTest() throws Exception {
        FilePath workspace = new FilePath(new File("/path/to/workspace"));
        String viewName = "normalized_view_name";
        ClearCaseUcmSCM scm = new ClearCaseUcmSCM(viewName, null, false, false, null, null, false,
                null, 0, null, false, "", "");
        scm.setEnv(new EnvVars());

        EnvVars env = new EnvVars();

        scm.publishEnvVars(workspace, env);

        Assert.assertEquals(viewName, env.get(AbstractClearCaseSCM.CLEARCASE_VIEWNAME_ENVSTR));
        Assert.assertEquals(workspace.child(viewName).getRemote(), env
                .get(AbstractClearCaseSCM.CLEARCASE_VIEWPATH_ENVSTR));
    }

    @Test
    public void getViewPathsTest() throws Exception {
        FilePath workspace = new FilePath(new File("/path/to/workspace"));

        String loadRules = "/vobs/VOB1/compA\nload /vobs/VOB2/compC\nvobs/VOB3/compY/${VAR}";
        EnvVars env = new EnvVars("VAR", "value");
        
        PowerMockito.mockStatic(Tools.class);
        when(Tools.isWindows(workspace)).thenReturn(false);
        when(Tools.convertPathForOS(Mockito.anyString(), Mockito.anyBoolean()))
                .thenCallRealMethod();

        ClearCaseUcmSCM scm = new ClearCaseUcmSCM("viewName", null, false, false, null, loadRules,
                false, null, 0, null, false, "", "");
        scm.setEnv(env);
        
        List<String> list = new ArrayList<String>();
        list.add("vobs/VOB1/compA");
        list.add("vobs/VOB2/compC");
        list.add("vobs/VOB3/compY/value");

        Assert.assertEquals(list, scm.getViewPaths(workspace));
    }

}
