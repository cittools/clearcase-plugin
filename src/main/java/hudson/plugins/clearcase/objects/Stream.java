package hudson.plugins.clearcase.objects;

import java.util.ArrayList;
import java.util.List;

public class Stream {

    public enum LockState {
        UNLOCKED, LOCKED, OBSOLETE, NONE;
        
        public static LockState value(String str) {
            try {
                return valueOf(str.trim().toUpperCase());
            } catch (Exception ex) {
                return NONE;
            }
        }
    }
    
    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private String name;
    private String pvob;
    private LockState lockState;
    private List<Component> components;
 
    /*******************************
     **** CONSTRUCTORS *************
     *******************************/
    
    public Stream(String nameWithPvob) {
        if (nameWithPvob == null) {
            nameWithPvob = "";
        }
        if (nameWithPvob.contains("@")) {
            String[] tab = nameWithPvob.split("@");
            this.name = tab[0];
            this.pvob = tab[1];
        } else {
            this.name = nameWithPvob;
        }
    }
    
    public Stream(String nameWithPvob, List<Component> components) {
        this(nameWithPvob);
        this.components = components;
    }
    
    public Stream(String name, String pvob) {
        super();
        this.name = name;
        this.pvob = pvob;
        components = new ArrayList<Component>();
    }

    public Stream(String name, String pvob, List<Component> components) {
        super();
        this.name = name;
        this.pvob = pvob;
        this.components = components;
    }
    
    public Stream() {
        
    }
    
    /*******************************
     **** METHODS ******************
     *******************************/

    @Override
    public String toString() {
        if (this.pvob != null) {
            return this.name + "@" + this.pvob;
        } else {
            return this.name;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        try {
            Stream other = (Stream) obj;
            return this.name.equals(other.name) && this.pvob.equals(other.pvob);
        } catch (Exception e) {
            return false;
        }
    }

    /*******************************
     **** GETTERS & SETTERS ********
     *******************************/
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPvob() {
        return pvob;
    }

    public void setPvob(String pvob) {
        this.pvob = pvob;
    }

    public LockState getLockState() {
        return lockState;
    }

    public void setLockState(LockState lockState) {
        this.lockState = lockState;
    }
    
    public List<Component> getComponents() {
        return components;
    }
    
    public void setComponents(List<Component> components) {
        this.components = components;
    }
}
