package hudson.plugins.clearcase.objects;

import hudson.plugins.clearcase.util.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a ClearCase view config spec.
 * 
 * It contains all the logic used to make transformations on it such as:
 * <ul>
 * <li>Detect, add or remove load rules</li>
 * <li>Construct a config spec that "shows" one or several labels/baselines</li>
 * </ul>
 * 
 * @author Robin Jarry
 */
public class ConfigSpec {

    private static final Pattern LOAD_RULE_PATTERN = Pattern.compile("^\\s*load\\s+(.+)\\s*$",
            Pattern.MULTILINE);
    /*
     * This regular expression is quite tricky. It matches any config spec line that contains a
     * "LATEST" rule but only if that line does NOT already contain a "-time" rule.
     */
    private static final Pattern LATEST_PATTERN = Pattern.compile(
            "^(.+)LATEST(?!.*\\s-time\\s.*)(.*)$", Pattern.MULTILINE);

    private String value;

    // // CONSTRUCTORS ////////////////////////////////////////////////////////////
    public ConfigSpec(String value) {
        this.value = value.trim();
    }

    /**
     * Produce a new config spec from an old one
     */
    public ConfigSpec(ConfigSpec otherConfigSpec) {
        this.value = otherConfigSpec.value;
    }

    /**
     * Produce a new config spec from an old one with new load rules
     */
    public ConfigSpec(ConfigSpec otherConfigSpec, List<String> newLoadRules, boolean windows) {
        this(otherConfigSpec);
        replaceLoadRules(newLoadRules, windows);
    }

    // ////////////////////////////////////////////////////////////////////////////
    /**
     * Extracts a set of load rules from a config spec without leading slashes or backslashes
     */
    public List<String> extractLoadRules() {
        List<String> loadRules = new ArrayList<String>();
        Matcher matcher = LOAD_RULE_PATTERN.matcher(this.value);
        while (matcher.find()) {
            String rule = matcher.group(1);
            while (rule.startsWith("/") || rule.startsWith("\\")) {
                rule = rule.substring(1);
            }
            loadRules.add(rule.trim());
        }
        return loadRules;
    }

    public void replaceLoadRules(List<String> newLoadRules, boolean windows) {
        removeLoadRules();
        for (String loadRule : newLoadRules) {
            value += Tools.convertPathForOS("\nload " + File.separator + loadRule, windows);
        }
        value += '\n';
        value = this.value.trim();
    }

    /**
     * Remove the load rules from a config spec
     * 
     * @param oldConfigSpec
     * @return
     */
    public void removeLoadRules() {
        value = LOAD_RULE_PATTERN.matcher(value).replaceAll("").trim();
    }

    public boolean loadRulesDiffer(List<String> loadRules) {
        List<String> thisLoadRules = extractLoadRules();
        for (String loadRule : thisLoadRules) {
            if (!loadRules.contains(loadRule)) {
                return true;
            }
        }
        for (String loadRule : loadRules) {
            if (!thisLoadRules.contains(loadRule)) {
                return true;
            }
        }
        return false;
    }

    public void addTimeRules(Date time) {
        String timeStr;
        if (time == null) {
            timeStr = "now";
        } else {
            timeStr = Tools.formatCleartoolDate(time);
        }
        value = LATEST_PATTERN.matcher(value).replaceAll("$1LATEST -time " + timeStr + "$2");
    }

    // // ACCESSORS ////////////////////////////////////////////////////////////
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    // /// UTILS ////////////////////////////////////////////////////////////////

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if (obj != null && obj instanceof ConfigSpec) {
            ConfigSpec other = (ConfigSpec) obj;
            if (this.value != null && other.value != null) {
                /* we must ignore the line endings here */
                String otherValue = Tools.convertPathForOS(other.value, false);
                String thisValue = Tools.convertPathForOS(this.value, false);
                result = otherValue.equals(thisValue);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
