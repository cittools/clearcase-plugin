package hudson.plugins.clearcase.objects;

import java.util.ArrayList;
import java.util.List;

public class CompositeComponent extends Component {

    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private List<Component> attachedComponents;
   
    /*******************************
     **** CONSTRUCTORS *************
     *******************************/
    
    public CompositeComponent(Component comp) {
        super(comp.getName(), comp.getPvob(), comp.isReadOnly());
        attachedComponents = new ArrayList<Component>();
    }
    
    public CompositeComponent(String nameWithPvob) {
        super(nameWithPvob);
        attachedComponents = new ArrayList<Component>();
    }
    
    public CompositeComponent(String nameWithPvob, boolean readOnly) {
        super(nameWithPvob, readOnly);
        attachedComponents = new ArrayList<Component>();
    }

    public CompositeComponent(String name, String pvob) {
        super(name, pvob);
        attachedComponents = new ArrayList<Component>();
    }
    
    public CompositeComponent(String name, String pvob, boolean readOnly) {
        super(name, pvob, readOnly);
        attachedComponents = new ArrayList<Component>();
    }

    public CompositeComponent(String nameWithPvob, List<Component> attachedComponents) {
        super(nameWithPvob);
        this.attachedComponents = attachedComponents;
    }
    
    public CompositeComponent(String name, String pvob, List<Component> attachedComponents) {
        super(name, pvob);
        this.attachedComponents = attachedComponents;
    }
    
    /*******************************
     **** METHODS ******************
     *******************************/
    
    @Override
    public boolean isComposite() {
        return true;
    }
    
    /*******************************
     **** GETTERS & SETTERS ********
     *******************************/
    
    public List<Component> getAttachedComponents() {
        return attachedComponents;
    }

    public void setAttachedComponents(List<Component> attachedComponents) {
        this.attachedComponents = attachedComponents;
    }
    
}
