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
 * 	<li>Detect, add or remove load rules</li>
 *  <li>Construct a config spec that "shows" one or several labels/baselines</li>
 * </ul>
 * 
 * @author Robin Jarry
 */
public class ConfigSpec {

	private static final Pattern LOAD_RULE_PATTERN = Pattern.compile("^\\s+load\\s+(.+)\\s+$");
	
	private final String configSpec;
	
	
	public ConfigSpec(String configSpec) {
		this.configSpec = configSpec;
	}
	
	
	
	
	/**
     * Extracts a set of load rules from a config spec without leading slashes or backslashes
     *
     * @param configSpec
     * @return load rules
     */
    public List<String> extractLoadRules() {
    	List<String> loadRules = new ArrayList<String>();
        Matcher matcher = LOAD_RULE_PATTERN.matcher(this.configSpec);
        while (matcher.find()){
            String rule = matcher.group(1);
            while (rule.startsWith("/") || rule.startsWith("\\")) {
                rule = rule.substring(1);
            }
            loadRules.add(rule.trim());
        }
        return loadRules;
    }
    
    /**
     * Produce a new config spec from an old one and a new set of load rules
     * 
     * @param oldConfigSpec
     * @param newLoadRules
     * @return
     */
    protected String makeNewConfigSpec(List<String> newLoadRules, boolean windows) {
        StringBuilder newConfigSpec = new StringBuilder(removeOldLoadRules(this.configSpec));
        for (String loadRule : newLoadRules) {
            newConfigSpec.append("\nload " + File.separator + loadRule);
        }
        newConfigSpec.append('\n');
        
        return Tools.convertPathForOS(newConfigSpec.toString(), windows);
    }

    /**
     * Remove the load rules from a config spec
     * 
     * @param oldConfigSpec
     * @return
     */
    private static String removeOldLoadRules(String oldConfigSpec) {
        return oldConfigSpec.replaceAll(LOAD_RULE_PATTERN.pattern(), "").trim();
    }
    
    protected boolean loadRulesChanged(List<String> oldLoadRules, List<String> newLoadRules) {
        for (String loadRule : oldLoadRules) {
            if (!newLoadRules.contains(loadRule)) {
                return true;
            }
        }
        for (String loadRule : newLoadRules) {
            if (!oldLoadRules.contains(loadRule)) {
                return true;
            }
        }
        return false;
    }
    
    protected boolean configSpecChanged(String oldConfigSpec, String newConfigSpec) {
        if (oldConfigSpec != null && newConfigSpec != null) {
            return !oldConfigSpec.trim().equals(newConfigSpec.trim());
        } else {
            return true;
        }
    }
    
    
    
    
    
    
    
	
}
