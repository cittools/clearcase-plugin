package hudson.plugins.clearcase.util;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.StringParameterValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.kohsuke.stapler.StaplerProxy;

public class CCParametersAction implements EnvironmentContributingAction, StaplerProxy {

    public static final String DISPLAY_NAME = "Clearcase Variables";

    ////////////////////
    // PRIVATE FIELDS //
    ////////////////////
    private final List<StringParameterValue> parameters;
    
    //////////////////
    // CONSTRUCTORS //
    //////////////////
    public CCParametersAction() {
        this.parameters = new ArrayList<StringParameterValue>();
    }

    public CCParametersAction(List<StringParameterValue> parameters) {
        this.parameters = parameters;
    }

    public CCParametersAction(StringParameterValue... parameters) {
        this.parameters = Arrays.asList(parameters);
    }

    //////////////////////////////////////////////////////////
    // OVERRIDEN METHODS FROM EnvironmentContributingAction //
    //////////////////////////////////////////////////////////
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }


    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        for (StringParameterValue p : parameters) {
            env.put(p.getName(), p.value);
        }
    }

    @Override
    public String getIconFileName() {
        return "/plugin/clearcase-thales/icons/clearcase-24x24.png";
    }

    @Override
    public String getUrlName() {
        return "clearcasevariables";
    }

    @Override
    public Object getTarget() {
        return new ClearCaseVariableList(parameters);
    }

    /////////////
    // GETTERS //
    /////////////
    
    public List<StringParameterValue> getParameters() {
        return parameters;
    }
    
    public StringParameterValue getParameter(String key) {
        for (StringParameterValue p : parameters) {
            if (p.getName().equals(key)) {
                return p;
            }
        }
        return null;
    }
    
    public static StringParameterValue getBuildParameter(AbstractBuild<?, ?> build, String key) {
        CCParametersAction action = build.getAction(CCParametersAction.class);

        if (action == null) {
            action = new CCParametersAction();
            build.addAction(action);
        }

        return action.getParameter(key);
    }
    
    public static void addBuildParameter(AbstractBuild<?, ?> build, StringParameterValue param) {
        CCParametersAction action = build.getAction(CCParametersAction.class);

        if (action == null) {
            action = new CCParametersAction();
            build.addAction(action);
        }

        action.getParameters().add(param);
    }
    
}





