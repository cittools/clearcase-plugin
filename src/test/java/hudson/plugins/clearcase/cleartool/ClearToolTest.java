package hudson.plugins.clearcase.cleartool;

import static hudson.plugins.clearcase.cleartool.HistoryFormatHandler.COMMENT;
import static hudson.plugins.clearcase.cleartool.HistoryFormatHandler.LINEEND;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.plugins.clearcase.history.UcmHistoryAction;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.CompositeComponent;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.Stream.LockState;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.Tools;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareOnlyThisForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class ClearToolTest {

    CTLauncher launcher;
    ClearTool ct;
    FilePath workspace;
    EnvVars env;
    File logFile;

    @Before
    public void setUp() throws Exception {
        launcher = mock(CTLauncher.class);
        ct = new ClearToolSnapshot(launcher);
        workspace = new FilePath(new File("."));
        env = new EnvVars();
        logFile = new File("logFile");

        when(launcher.getWorkspace()).thenReturn(workspace);
        when(launcher.getEnv()).thenReturn(env);
        when(launcher.getLogFile()).thenReturn(logFile);

    }

    @Test
    public void dynamicConstructorTest() throws Exception {
        FilePath viewDrive = new FilePath(new File("."));
        ct = new ClearToolDynamic(launcher, viewDrive);

        assertEquals("BUILT", ct.print(PromotionLevel.BUILT));
        assertEquals("REJECTED", ct.print(PromotionLevel.REJECTED));
        assertEquals("RELEASED", ct.print(PromotionLevel.RELEASED));

        assertEquals(viewDrive, ct.getViewRootPath());
    }

    @Test
    public void snapshotConstructorTest() throws Exception {
        ct = new ClearToolSnapshot(launcher);

        assertEquals("BUILT", ct.print(PromotionLevel.BUILT));
        assertEquals("REJECTED", ct.print(PromotionLevel.REJECTED));
        assertEquals("RELEASED", ct.print(PromotionLevel.RELEASED));

        assertEquals(workspace, ct.getViewRootPath());
    }

    @Test
    public void miscMethodsTest() throws Exception {
        assertEquals(workspace, ct.getWorkspace());
        assertEquals(launcher, ct.getLauncher());
        assertEquals(env, ct.getEnv());
        assertEquals(logFile, ct.getLogFile());
    }

    @Test
    public void mkviewTest() throws Exception {
        View v = new View("viewName", null, false);
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkview"));

        ct.mkview(v, null, null);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkview", "-snapshot", "-ptime", "-tag",
                v.getName(), v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace));
    }

    @Test
    public void mkviewDynamicUcmWithStglocTest() throws Exception {
        Stream stream = new Stream("stream@/vobtag");
        View v = new View("viewName", stream, true);
        v.setStream(stream);
        String stgloc = "stgloc";
        String params = "mkview optional params";

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkview"));
        ct.mkview(v, stgloc, params);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkview", "-tag", v.getName(),
                "-stgloc", stgloc, "-stream", stream.toString());
        args.addTokenized(params);
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void mkviewDynamicWithoutStglocTest() throws Exception {
        View v = new View("viewName", null, true);

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkview"));
        try {
            ct.mkview(v, null, null);
            fail("create dynamic view without stgloc shouldn't be possible");
        } catch (ClearToolError e) {
            /* pass */
        }
    }

    @Test
    public void makeBaselinesTest() throws Exception {
        View v = new View("viewName", null, false);
        String baseName = "basename";
        String comment = "comment";

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkbl_no_changes"));
        List<Baseline> nobl = ct.makeBaselines(v, null, false, true, baseName, comment);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkbl", "-comment", comment, "-all",
                "-full", baseName);
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals(0, nobl.size());
    }

    @Test
    public void makeBaselinesChangesTest() throws Exception {
        View v = new View("viewName", null, false);
        String baseName = "basename";
        String comment = "comment";
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkbl_changes"));
        List<Baseline> bls = ct.makeBaselines(v, null, false, true, baseName, "comment");

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkbl", "-comment", comment, "-all",
                "-full", baseName);
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals(new Baseline("basename.1000"), bls.get(0));
        assertEquals(new Baseline("basename.2000"), bls.get(1));
    }

    @Test
    public void makeBaselinesRestrictComponentsTest() throws Exception {
        View v = new View("viewName", null, false);
        String baseName = "basename";
        Component comp1 = new Component("comp1@/vobs/P_exercice");
        Component comp2 = new Component("comp4@/vobs/P_exercice");
        Component comp3 = new Component("comp6@/vobs/P_exercice");

        List<Component> comps = new ArrayList<Component>();
        comps.add(comp1);
        comps.add(comp2);
        comps.add(comp3);
        String compList = comps.toString().replaceAll("[\\s\\[\\]]", "");

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkbl_restrict_components"));

        List<Baseline> restrictedBls = ct.makeBaselines(v, comps, true, false, baseName, null);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkbl", "-nc", "-comp", compList,
                "-identical", "-incremental", baseName);
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals(3, restrictedBls.size());
        assertEquals(new Baseline("basename.1000"), restrictedBls.get(0));
        assertEquals(new Baseline("basename.2000"), restrictedBls.get(1));
        assertEquals(new Baseline("basename.3000"), restrictedBls.get(2));

        assertEquals(comp1, restrictedBls.get(0).getComponent());
        assertEquals(comp1.getPvob(), restrictedBls.get(0).getComponent().getPvob());
        assertEquals(comp2, restrictedBls.get(1).getComponent());
        assertEquals(comp2.getPvob(), restrictedBls.get(1).getComponent().getPvob());
        assertEquals(comp3, restrictedBls.get(2).getComponent());
        assertEquals(comp3.getPvob(), restrictedBls.get(2).getComponent().getPvob());
    }

    @Test
    public void makeBaselineCompositeTest() throws Exception {
        View v = new View("viewName", null, true);
        String baseName = "basename";

        Component comp1, comp2, comp3;
        comp1 = new Component("comp1@/vobs/P_exercice");
        comp2 = new Component("comp4@/vobs/P_exercice");
        comp3 = new Component("comp6@/vobs/P_exercice");
        List<Component> comps = new ArrayList<Component>();
        comps.add(comp1);
        comps.add(comp2);
        comps.add(comp3);

        CompositeComponent cComp = new CompositeComponent("composite@/vobs/p_vob", comps);
        String compList = cComp.getAttachedComponents().toString().replaceAll("[\\s\\[\\]]", "");

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("mkbl_composite_component"));

        List<Baseline> bls = ct.makeCompositeBaseline(v, cComp, false, true, baseName, null);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("mkbl", "-nc", "-comp",
                cComp.toString(), "-view", v.getName(), "-ddepends_on", compList, "-adepends_on",
                compList, "-full", baseName);
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertEquals(1, bls.size());
        assertEquals(cComp, bls.get(0).getComponent());
        assertEquals(cComp.getPvob(), bls.get(0).getComponent().getPvob());
    }

    @Test
    public void getDependingBaselinesTest() throws Exception {

        Baseline baseline = new Baseline("composite_baseline@/vobtag");

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("depending_bls"));

        List<Baseline> expectedBls = new ArrayList<Baseline>();

        expectedBls.add(new Baseline("baseline1@/vobtag"));
        expectedBls.add(new Baseline("baseline2@/vobtag"));
        expectedBls.add(new Baseline("baseline3@/vobtag"));
        expectedBls.add(new Baseline("baseline4@/vobtag"));

        List<Baseline> bls = ct.getDependingBaselines(baseline);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsbl", "-fmt", "%[depends_on]p",
                baseline.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertEquals(expectedBls, bls);
    }

    @Test
    public void getLatestBaselineTest() throws Exception {
        Component comp = new Component("comp@/pvob");
        Stream stream = new Stream("stream@/pvob");

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("latest_bl"));

        Baseline latestBl = ct.getLatestBl(comp, stream);

        Baseline expectedBl = new Baseline("bl5@/vobtag");

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsbl", "-fmt", ",%Nd;%Xn", "-comp",
                comp.toString(), "-stream", stream.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertEquals(expectedBl, latestBl);
    }

    @Test
    public void recommendAllEligibleBaselinesTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("recommend"));
        Stream stream = new Stream("stream@vobtag");
        ct.recommendAllEligibleBaselines(stream);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("chstream", "-recommended", "-default",
                stream.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void rmviewSnapshotTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("rmview_snapshot"));

        View v = new View("viewName", null, false);

        ct.rmview(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("rmview", "-force", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace));
    }

    @Test
    public void rmviewDynamicTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");

        View v = new View("viewName", null, true);

        ct.rmview(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("rmview", "-force", "-tag", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void rmtagTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");

        View v = new View("viewName", null, false);

        ct.rmtag(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("rmtag", "-view", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }
    
    @Test
    public void unregisterTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");

        View v = new View("viewName", null, false);
        v.setUuid("abcef0123456789");
        
        ct.unregister(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("unregister", "-view", "-uuid", v.getUuid());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }    
    
    @Test
    public void startViewTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");

        View v = new View("viewName", null, true);

        ct.startView(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("startview", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void getRWComponentsTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("components"));

        Stream stream = new Stream("stream@vobtag");
        List<Component> actual = ct.getRWComponents(stream);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-fmt", "%[mod_comps]Xp",
                stream.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        Component c1 = new Component("comp1@vobtag");
        Component c2 = new Component("comp2@vobtag");
        Component c3 = new Component("comp3@vobtag");
        List<Component> expected = new ArrayList<Component>();
        expected.add(c1);
        expected.add(c2);
        expected.add(c3);

        assertEquals(expected, actual);
        for (Component c : actual) {
            assertFalse(c.isReadOnly());
        }
    }

    @Test
    public void getReadOnlyComponentsTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("components"));

        Stream stream = new Stream("stream@vobtag");
        List<Component> actual = ct.getReadOnlyComponents(stream);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-fmt",
                "%[non_mod_comps]Xp", stream.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        Component c1 = new Component("comp1@vobtag");
        Component c2 = new Component("comp2@vobtag");
        Component c3 = new Component("comp3@vobtag");
        List<Component> expected = new ArrayList<Component>();
        expected.add(c1);
        expected.add(c2);
        expected.add(c3);

        assertEquals(expected, actual);
        for (Component c : actual) {
            assertTrue(c.isReadOnly());
        }
    }

    @Test
    public void getComponentRootPathTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                "/root/dir");

        Component comp = new Component("comp1@vobtag");

        assertEquals("root/dir", ct.getComponentRootPath(comp));

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lscomp", "-fmt", "%[root_dir]p", comp
                .toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void updateUcmTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("update_snapshot_ucm"));
        View v = new View("viewName", new Stream("stream@vobtag"), false);

        ct.update(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("setcs", "-force", "-stream");
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));
    }

    @Test
    public void updateBaseTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("update_snapshot_base"));
        View v = new View("viewName", false);

        ct.update(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("setcs", "-force", "-current");
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));
    }

    @Test
    public void updateDynamicTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("update_snapshot_base"));
        View v = new View("viewName", true);

        ct.update(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("setcs", "-tag", v.getName(), "-current");
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));
    }

    @Test
    public void getStreamFromUcmViewTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("stream_from_view"));
        View v = new View("viewName", true);

        Stream actual = ct.getStreamFromView(v);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-fmt", "%Xn", "-view", v
                .getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        Stream expected = new Stream("streamName@vobtag");

        assertEquals(expected, actual);
    }

    @Test
    public void getStreamFromBaseViewTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        View v = new View("viewName", true);

        try {
            ct.getStreamFromView(v);
        } catch (ClearToolError e) {
            return;
        }
        fail("lsstream on a non-ucm view should trigger a ClearToolError");
    }

    @Test
    public void rebaseDynamicViewTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        View v = new View("viewName", new Stream("stream@vobtag"), true);

        List<Baseline> baselines = new ArrayList<Baseline>();
        baselines.add(new Baseline("baseline1@vobtag"));
        baselines.add(new Baseline("baseline2@vobtag"));
        baselines.add(new Baseline("baseline3@vobtag"));

        ct.rebaseDynamicView(v, baselines);

        String blString = baselines.toString().replaceAll("[\\s\\[\\]]", "");
        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("rebase", "-baseline", blString,
                "-view", v.getName(), "-complete");
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void lsHistoryUcmTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lshistory_ucm"));

        // test setup
        View v = new View("viewName", new Stream("stream@vobtag"), false);
        Date date = new Date(0);
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy.HH:mm:ss", Locale.US);
        int offset = TimeZone.getDefault().getOffset(date.getTime()) / (1000 * 60 * 60);
        String dateStr = formatter.format(date).toLowerCase();
        dateStr += String.format("UTC%+d", offset);
        HistoryFormatHandler formatHandler = new HistoryFormatHandler(
                UcmHistoryAction.HISTORY_FORMAT);
        String fmt_ccase = formatHandler.getFormat() + COMMENT + LINEEND;
        List<String> lookupPaths = new ArrayList<String>();
        lookupPaths.add("path/1");
        lookupPaths.add("path/2");
        lookupPaths.add("path/3");

        List<HistoryEntry> entries = ct.lshistory(formatHandler, date, v, "branch", lookupPaths,
                "/extended/view/path");

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lshistory", "-since", dateStr,
                "-fmt", fmt_ccase, "-branch", "brtype:branch", "-nco", "-r");
        for (String path : lookupPaths) {
            args.add(path);
        }
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals(5, entries.size());
        assertEquals("user", entries.get(0).getUser());
        assertEquals("2010-10-18 11:57:52", entries.get(0).getDateText());
        assertEquals("/vobs/vob/component/path/path/file4", entries.get(3).getElement());
        assertEquals("/main/branch/9", entries.get(2).getVersionId());
        assertEquals("checkin", entries.get(1).getOperation());
        assertEquals("create version", entries.get(4).getEvent());
        assertEquals("deliver.devbranch.20101018.120019", entries.get(2).getActivityName());
    }

    @Test
    public void lsIntegActivityTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lsactivity"));
        // test setup
        View v = new View("viewName", new Stream("stream@vobtag"), false);
        HistoryFormatHandler formatHandler = new HistoryFormatHandler(
                UcmHistoryAction.ACTIVITY_FORMAT);
        String fmt_ccase = formatHandler.getFormat();
        String activityName = "integration_activity";

        UcmActivity actual = ct.lsactivity(activityName, formatHandler, v);
        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsactivity", "-fmt", fmt_ccase,
                activityName);
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals("user", actual.getUser());
        assertEquals("activity1 activity2 activity3", actual.getContribActivitiesStr());
        assertEquals("integration activity headline", actual.getHeadline());
        assertEquals("stream", actual.getStream());
        assertEquals("Integration activity created by deliver", actual.getComment());
    }

    @Test
    public void lsNormalActivityTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lsactivity_normal"));
        // test setup
        View v = new View("viewName", new Stream("stream@vobtag"), false);
        HistoryFormatHandler formatHandler = new HistoryFormatHandler(
                UcmHistoryAction.ACTIVITY_FORMAT);
        String fmt_ccase = formatHandler.getFormat();
        String activityName = "activity";

        UcmActivity actual = ct.lsactivity(activityName, formatHandler, v);
        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsactivity", "-fmt", fmt_ccase,
                activityName);
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(v.getName())));

        assertEquals("user", actual.getUser());
        assertEquals("", actual.getContribActivitiesStr());
        assertEquals("activity headline", actual.getHeadline());
        assertEquals("stream", actual.getStream());
        assertEquals("", actual.getComment());
    }

    @Test
    public void catcsTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("configspec"));
        // test setup
        View v = new View("viewName", null, false);

        String actual = ct.catcs(v);
        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("catcs", "-tag", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertEquals(ctResult("configspec"), actual);
    }

    @Test
    public void setcsTest() throws Exception {
        String configSpec = ctResult("configspec");

        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                configSpec);
        // test setup
        View v = new View("viewName", null, true);

        ct.setcs(v, configSpec);
    }

    @Test
    public void getComponentFromBLTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("component_from_bl"));
        // test setup
        Baseline bl = new Baseline("baseline@pvob");

        Component actual = ct.getComponentFromBL(bl);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsbl", "-fmt", "%[component]Xp", bl
                .toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        Component expected = new Component("component@vobtag");
        assertEquals(expected, actual);
    }

    @Test
    public void getLatestBaselinesTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("latest_baselines"));
        // test setup
        Stream s = new Stream("stream@vobtag");

        List<Baseline> actual = ct.getLatestBaselines(s);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-fmt", "%[latest_bls]Xp", s
                .toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        List<Baseline> expected = new ArrayList<Baseline>();
        expected.add(new Baseline("baseline1@vobtag"));
        expected.add(new Baseline("baseline2@vobtag"));
        expected.add(new Baseline("baseline3@vobtag"));

        assertEquals(expected, actual);
    }

    @Test
    public void changeBaselinePromotionLevelTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        // test setup
        Baseline bl = new Baseline("baseline@pvob");
        PromotionLevel level = PromotionLevel.BUILT;

        ct.changeBaselinePromotionLevel(bl, level);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("chbl", "-c",
                "Hudson changed promotion level to " + ct.print(level), "-level", ct.print(level),
                bl.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void lsvobTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lsvob"));

        List<String> actual = ct.lsvob();

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsvob", "-s");
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        List<String> expected = Arrays.asList(ctResult("lsvob").trim().split("\\n"));

        assertEquals(expected, actual);
    }

    @Test
    public void getAllStreamsFromVobTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("streams_from_vob"));
        String vobTag = "vobtag";
        List<Stream> actual = ct.getAllStreamsFromVob(vobTag);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-s", "-invob", vobTag);
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        List<Stream> expected = new ArrayList<Stream>();
        expected.add(new Stream("stream1", vobTag));
        expected.add(new Stream("stream2", vobTag));
        expected.add(new Stream("stream3", vobTag));
        expected.add(new Stream("stream4", vobTag));

        assertEquals(expected, actual);
    }

    @Test
    public void fetchPromotionLevelsTest() throws Exception {
        String vobTag = "vobtag";
        ArgumentListBuilder args1 = new ArgumentListBuilder("describe", "-fmt", "%["
                + ClearTool.REJECTED_PLEVEL_ATTR + "]NSa", "vob:" + vobTag);
        ArgumentListBuilder args2 = new ArgumentListBuilder("describe", "-fmt", "%["
                + ClearTool.BUILT_PLEVEL_ATTR + "]NSa", "vob:" + vobTag);
        ArgumentListBuilder args3 = new ArgumentListBuilder("describe", "-fmt", "%["
                + ClearTool.RELEASED_PLEVEL_ATTR + "]NSa", "vob:" + vobTag);

        String customRejected = "CUSTOM_REJECTED";
        String customBuilt = "CUSTOM_BUILT";
        String customReleased = "CUSTOM_RELEASED";

        when(launcher.run(argThat(new IsSameArgs(args1)), any(FilePath.class))).thenReturn(
                '"' + customRejected + '"');
        when(launcher.run(argThat(new IsSameArgs(args2)), any(FilePath.class))).thenReturn(
                '"' + customBuilt + '"');
        when(launcher.run(argThat(new IsSameArgs(args3)), any(FilePath.class))).thenReturn(
                '"' + customReleased + '"');

        ct.fetchPromotionLevels(vobTag);

        // verify command line
        verify(launcher).run(argThat(new IsSameArgs(args1)), (FilePath) isNull());
        verify(launcher).run(argThat(new IsSameArgs(args2)), (FilePath) isNull());
        verify(launcher).run(argThat(new IsSameArgs(args3)), (FilePath) isNull());

        assertEquals(customRejected, ct.print(PromotionLevel.REJECTED));
        assertEquals(customBuilt, ct.print(PromotionLevel.BUILT));
        assertEquals(customReleased, ct.print(PromotionLevel.RELEASED));
    }

    @Test
    public void attributeNotFoundTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        String attrName = "unbound_attribute";
        String vobTag = "vob";
        try {
            ct.getAttributeFromVob(attrName, vobTag);
            fail("Attribute does not exist. Command should fail.");
        } catch (ClearToolError e) {
            assertEquals("Attribute: " + attrName + " not found on vob: " + vobTag, e.getMessage());
        }
    }

    @Test
    public void lockStreamTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        Stream s = new Stream("stream@vobtag");
        ct.lockStream(s);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lock", "-c",
                "Hudson locked the stream", "stream:" + s.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void unlockStreamTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        Stream s = new Stream("stream@vobtag");
        ct.unlockStream(s);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("unlock", "-c",
                "Hudson unlocked the stream", "stream:" + s.toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());
    }

    @Test
    public void getViewInfoTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("view_info"));
        View v = new View("view_tag");

        assertTrue(ct.getViewInfo(v));

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsview", "-l", "-prop", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertTrue(v.isDynamic());
        assertTrue(v.isUcm());
        assertEquals("86fa47219e1a11df9917000183dd5c77", v.getUuid());
        assertEquals("/storage/views/view_tag.vws", v.getGlobalPath());
        assertEquals("/storage/views/view_tag.vws", v.getLocalPath());
    }
    
    @Test
    public void getViewInfoBaseTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("view_info_base"));
        View v = new View("test_base_cc_dyn");

        assertTrue(ct.getViewInfo(v));

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsview", "-l", "-prop", v.getName());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertTrue(v.isDynamic());
        assertFalse(v.isUcm());
        assertEquals("4edf197539cb11e08345005056925062", v.getUuid());
        assertEquals("/cc_stg/views/test_base_cc_dyn.vws", v.getGlobalPath());
        assertEquals("/cc_stg/views/test_base_cc_dyn.vws", v.getLocalPath());
    }
    

    @Test
    public void getViewsFromStreamTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("views_from_stream"));
        Stream s = new Stream("stream@vobtag");
        List<View> views = ct.getViewsFromStream(s);

        // verify command line
        ArgumentListBuilder args = new ArgumentListBuilder("lsstream", "-fmt", "%[views]p", s
                .toString());
        verify(launcher).run(argThat(new IsSameArgs(args)), (FilePath) isNull());

        assertEquals(5, views.size());
        for (View v : views) {
            assertEquals(s, v.getStream());
        }
    }

    @Test
    @PrepareOnlyThisForTest( { Tools.class })
    public void getSnapshotViewUuidWindowsTest() throws Exception {
        PowerMockito.mockStatic(Tools.class);
        when(Tools.isWindows(any(FilePath.class))).thenReturn(true);
        
        String viewFolder = this.getClass().getResource(".").getFile();
        String viewDat = "ws_oid:azefa654abbcc6546598798797898 view_uuid:64163232363525zzeaazeaz2";
        FilePath viewDatFile = new FilePath(new File(viewFolder)).child("view.dat");
        
        viewDatFile.write(viewDat, "ASCII");
        
        String uuid = ct.getSnapshotViewUuid(new FilePath(new File(viewFolder)));

        assertEquals("64163232363525zzeaazeaz2", uuid);
        
        viewDatFile.delete();
    }

    @Test
    @PrepareOnlyThisForTest( { Tools.class })
    public void getSnapshotViewUuidUnixTest() throws Exception {
        PowerMockito.mockStatic(Tools.class);
        when(Tools.isWindows(any(FilePath.class))).thenReturn(false);
        
        String viewFolder = this.getClass().getResource(".").getFile();
        String viewDat = "ws_oid:azefa654abbcc6546598798797898 view_uuid:64163232363525zzeaazeaz2";
        FilePath viewDatFile = new FilePath(new File(viewFolder)).child(".view.dat");
        
        viewDatFile.write(viewDat, "ASCII");
        
        String uuid = ct.getSnapshotViewUuid(new FilePath(new File(viewFolder)));

        assertEquals("64163232363525zzeaazeaz2", uuid);
        
        viewDatFile.delete();
    }

    @Test
    public void lsStglocTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lsstgloc"));

        List<String> stglocs = ct.lsStgloc();

        String[] expected = { "snapviews", "snapviews2", "snap_views", "viewstore", "vobstore" };

        assertEquals(Arrays.asList(expected), stglocs);
    }

    @Test
    public void getStreamLockStateTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class)))
                .thenReturn("locked");
        assertEquals(LockState.LOCKED, ct.getStreamLockState(new Stream("stream@vobtag")));
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                "obsolete");
        assertEquals(LockState.OBSOLETE, ct.getStreamLockState(new Stream("stream@vobtag")));
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                "unlocked");
        assertEquals(LockState.UNLOCKED, ct.getStreamLockState(new Stream("stream@vobtag")));
    }

    @Test
    public void hasCheckoutsNoneTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn("");
        View view = new View("viewname");
        view.setDynamic(true);
        assertFalse(ct.hasCheckouts("branch", view, null));
    }
    
    @Test
    public void hasCheckoutsSnapshotTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lscheckout"));
        
        String branch = "branch";
        View view = new View("viewname");
        List<String> viewPaths = new ArrayList<String>();
        viewPaths.add("vobs/vob1/comp1");
        viewPaths.add("vobs/vob2/comp2");
        
        ArgumentListBuilder args = new ArgumentListBuilder("lscheckout", "-s", 
                                                           "-brtype", branch, 
                                                           "-r", 
                                                           "vobs/vob1/comp1", 
                                                           "vobs/vob2/comp2");
        assertTrue(ct.hasCheckouts(branch, view, viewPaths));
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(view.getName())));
        
        try {
            ct.hasCheckouts(branch, view, null);
            fail("This test should fail with error: " +
            		"'Cannot search for checkouts in a snapshot view without load rules'");
        } catch (ClearToolError e) {
            /* pass */
        }
    }

    @Test
    public void hasCheckoutsDynamicTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("lscheckout"));
        
        String branch = "branch";
        View view = new View("viewname");
        view.setDynamic(true);
        List<String> viewPaths = new ArrayList<String>();
        viewPaths.add("vobs/vob1/comp1");
        viewPaths.add("vobs/vob2/comp2");
        
        ArgumentListBuilder args = new ArgumentListBuilder("lscheckout", "-s", 
                                                           "-brtype", branch, 
                                                           "-avobs");
        assertTrue(ct.hasCheckouts(branch, view, viewPaths));
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(view.getName())));
    }
    
    @Test
    public void deliverOkTest() throws Exception {
        when(launcher.run(any(ArgumentListBuilder.class), any(FilePath.class))).thenReturn(
                ctResult("deliver_ok"));
        
        View view = new View("viewname");
        Stream devStream = new Stream("dev_stream@\\pvob");
        Baseline baseline = new Baseline("baseline@\\pvob");
        baseline.setStream(devStream);
        
        ArgumentListBuilder args = new ArgumentListBuilder("deliver", 
        		"-baseline", baseline.toString(),
        		"-stream", devStream.toString(),
        		"-to", view.getName(),
        		"-abort", "-force");
        
        ct.deliver(baseline, view, true);
        
        verify(launcher).run(argThat(new IsSameArgs(args)), eq(workspace.child(view.getName())));
    }

    /*******************************************************************************************/
    /*******************************************************************************************/
    /*******************************************************************************************/

    String ctResult(String commandName) throws Exception {
        InputStream ios = this.getClass().getResourceAsStream(commandName + ".txt");
        if (ios == null) {
            throw new FileNotFoundException(commandName + ".txt");
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(ios));

        StringBuffer sb = new StringBuffer();

        String line = r.readLine();
        while (line != null) {
            sb.append(line + "\n");
            line = r.readLine();
        }
        r.close();
        return sb.toString();
    }

    static class IsSameArgs extends ArgumentMatcher<ArgumentListBuilder> {

        List<String> arg;
        List<String> otherAsList;

        public IsSameArgs(ArgumentListBuilder arg) {
            super();
            this.arg = arg.toList();
        }

        @Override
        public boolean matches(Object obj) {
            if (obj instanceof ArgumentListBuilder) {
                ArgumentListBuilder other = (ArgumentListBuilder) obj;
                otherAsList = other.toList();
                return arg.equals(otherAsList);
            } else {
                return false;
            }
        }

        @Override
        public void describeTo(Description description) {
            description
                    .appendText("Arguments: " + this.arg + " expected\n" + "got: " + otherAsList);
        }
    }
}
