package hudson.plugins.clearcase.objects;


public class Component {

    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private String name;
    private String pvob;
    private String rootPath;
    private boolean readOnly = false;
    
    /*******************************
     **** CONSTRUCTORS *************
     *******************************/
    
    public Component(String nameWithPvob) {
        if (nameWithPvob.contains("@")) {
            String[] tab = nameWithPvob.split("@");
            this.name = tab[0];
            this.pvob = tab[1];
        } else {
            this.name = nameWithPvob;
        }
    }
    
    public Component(String nameWithPvob, boolean readOnly) {
        this(nameWithPvob);
        this.readOnly = readOnly;
    }
    
    public Component(String name, String pvob) {
        this.name = name;
        this.pvob = pvob;
    }

    public Component(String name, String pvob, boolean readOnly) {
        this.name = name;
        this.pvob = pvob;
        this.readOnly = readOnly;
    }
    
    /*******************************
     **** METHODS ******************
     *******************************/
    
    public boolean isComposite() {
        return false;
    }
    
    @Override
    public boolean equals(Object obj) {
        try {
            Component other = (Component) obj; 
            return this.name.equals(other.name) && this.pvob.equals(other.pvob);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String toString() {
        if (this.pvob != null) {
            return this.name + "@" + this.pvob;
        } else {
            return this.name;
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
    
    public void setRootPath(String root) {
        this.rootPath = root;
    }
    
    public String getRootPath() {
        return rootPath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
