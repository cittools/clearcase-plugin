package hudson.plugins.clearcase.cleartool;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ConfigSpecTest {

    final static String SELECT_RULES = "ucm\n" +
            "identity 8723874289798724:28489729874\n" +
            "element * CHECKEDOUT\n" +
            "element /vobs/test/... labelname -mkbranch dev_steam\n" +
            "#this is a comment\n" +
            "end ucm\n" +
            "element * /main/0\n";
    final static String LOAD_RULES = "load /vobs/test/folder1\n" +
            "#load /vobs/test/folder1/toto.txt\n" +
            "load /vobs/test/folder2\n" +
            "load /vobs/test/folder3\n";



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





}
