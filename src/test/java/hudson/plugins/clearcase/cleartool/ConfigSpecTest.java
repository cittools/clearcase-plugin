package hudson.plugins.clearcase.cleartool;

import static org.junit.Assert.*;

import hudson.plugins.clearcase.objects.ConfigSpec;
import hudson.plugins.clearcase.util.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class ConfigSpecTest {

    final static String SELECT_RULES = "ucm\n" + "identity 8723874289798724:28489729874\n"
            + "element * CHECKEDOUT\n"
            + "element /vobs/test/... .../dev_steam/LATEST -mkbranch dev_steam\n"
            + "element /vobs/test2/... .../dev_steam2/LATEST -time yesterday -mkbranch dev_steam\n"
            + "element /vobs/test/... labelname -mkbranch dev_steam\n" + "#this is a comment\n"
            + "end ucm\n" + "element * /main/0\n";
    final static String LOAD_RULES = "load /vobs/test/folder1\n"
            + "#load /vobs/test/folder1/toto.txt\n" + "load /vobs/test/folder2\n"
            + "load /vobs/test/folder3\n";

    @Test
    public void testExtractLoadRules() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES + LOAD_RULES);

        List<String> loadRules = new ArrayList<String>();
        loadRules.add("vobs/test/folder1");
        loadRules.add("vobs/test/folder2");
        loadRules.add("vobs/test/folder3");

        assertEquals(loadRules, cs.extractLoadRules());
    }

    @Test
    public void testRemoveLoadRules() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES + LOAD_RULES);
        cs.removeLoadRules();
        assertEquals(SELECT_RULES + "\n#load /vobs/test/folder1/toto.txt", cs.getValue().toString());
    }

    @Test
    public void testReplaceLoadRules() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES + LOAD_RULES);
        List<String> loadRules = new ArrayList<String>();
        loadRules.add("vobs/cuir/folder1");
        loadRules.add("vobs/cuir/folder2");
        loadRules.add("vobs/cuir/folder3");
        cs.replaceLoadRules(loadRules, false);
        assertEquals(loadRules, cs.extractLoadRules());
    }

    @Test
    public void testLoadRulesDiffer() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES + LOAD_RULES);
        List<String> loadRules = new ArrayList<String>();
        loadRules.add("vobs/cuir/folder1");
        loadRules.add("vobs/cuir/folder2");
        loadRules.add("vobs/cuir/folder3");
        assertTrue(cs.loadRulesDiffer(loadRules));
    }

    @Test
    public void testAddTimeRules() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES);

        Date time = Calendar.getInstance().getTime();
        cs.addTimeRules(time);

        String timeStr = Tools.formatCleartoolDate(time);

        assertTrue(cs.getValue().contains(".../dev_steam/LATEST -time " + timeStr));
        assertTrue(cs.getValue().contains(".../dev_steam2/LATEST -time " + timeStr));
    }

    @Test
    public void testAddTimeNowRules() {
        ConfigSpec cs = new ConfigSpec(SELECT_RULES);
        cs.addTimeRules(null);
        assertTrue(cs.getValue().contains(".../dev_steam/LATEST -time now"));
        assertTrue(cs.getValue().contains(".../dev_steam2/LATEST -time now"));
    }

    @Test
    public void testLatestInStreamName() throws IOException, URISyntaxException {

        String before = FileUtils.readFileToString(new File(getClass().getResource(
                "configspec_latest.txt").toURI()));
        String after = FileUtils.readFileToString(new File(getClass().getResource(
                "configspec_latest_modified.txt").toURI()));

        ConfigSpec cs = new ConfigSpec(before);

        cs.addTimeRules(null);
        assertEquals(after, cs.getValue());
    }

}
