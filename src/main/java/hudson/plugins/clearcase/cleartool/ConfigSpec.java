package hudson.plugins.clearcase.cleartool;

import hudson.plugins.clearcase.util.Tools;

import java.io.File;
import java.util.ArrayList;
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

    private final StringBuilder value;

    public ConfigSpec(String value) {
        this.value = new StringBuilder(value.trim());
    }

    /**
     * Produce a new config spec from an old one
     */
    public ConfigSpec(ConfigSpec otherConfigSpec) {
        this.value = new StringBuilder(otherConfigSpec.getValue().toString());
    }


    /**
     * Produce a new config spec from an old one with new load rules
     */
    public ConfigSpec(ConfigSpec otherConfigSpec, List<String> newLoadRules, boolean windows) {
        this(otherConfigSpec);
        replaceLoadRules(newLoadRules, windows);
    }


    /**
     * Extracts a set of load rules from a config spec without leading slashes
     * or backslashes
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
            value.append("\nload " + File.separator + loadRule);
        }
        value.append('\n');
        String newValue = Tools.convertPathForOS(this.value.toString(), windows);
        value.replace(0, value.length(), newValue.trim());
    }

    /**
     * Remove the load rules from a config spec
     *
     * @param oldConfigSpec
     * @return
     */
    public void removeLoadRules() {
        String newValue = LOAD_RULE_PATTERN.matcher(value).replaceAll("");
        value.replace(0, value.length(), newValue.trim());
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

    //// ACCESSORS /////
    public StringBuilder getValue() {
        return value;
    }

    ///// UTILS ////

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if (obj != null && obj instanceof ConfigSpec) {
            ConfigSpec other = (ConfigSpec) obj;
            if (other.getValue() != null) {
                result = other.value.equals(this.value);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
