package hudson.plugins.clearcase.util;

import hudson.model.Api;
import hudson.model.StringParameterValue;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class ClearCaseVariableList implements Serializable {

    private static final long serialVersionUID = -7068557693925062572L;
    
    private final List<ClearCaseVariable> variables = new ArrayList<ClearCaseVariable>();
    
    public ClearCaseVariableList(List<StringParameterValue> parameters) {
        for (StringParameterValue p : parameters) {
            variables.add(new ClearCaseVariable(p.getName(), p.value, p.getDescription()));
        }
    }
    
    @Exported
    public List<ClearCaseVariable> getVariables() {
        return variables;
    }
    
    
    public Object getDynamic(final String link, final StaplerRequest request, 
                             final StaplerResponse response) throws IOException 
    {
        response.sendRedirect2("index");
        return null;
    }
    
    public Api getApi() {
        return new Api(this);
    }
    
    @ExportedBean
    public class ClearCaseVariable implements Serializable {
        
        private static final long serialVersionUID = -779783978364516484L;

        private final String key;
        private final String value;
        private final String description;
        
        public ClearCaseVariable(String key, String value, String description) {
            this.key = key;
            this.value = value;
            this.description = description;
        }
        @Exported
        public String getKey() {
            return key;
        }
        @Exported
        public String getValue() {
            return value;
        }
        @Exported
        public String getDescription() {
            return description;
        }
    }
}
