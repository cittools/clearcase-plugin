package hudson.plugins.clearcase.objects;

public class Baseline {
    
    public static enum PromotionLevel {
        REJECTED, BUILT, RELEASED, NONE;
        
        public static final String DEFAULT_REJECTED = "REJECTED";
        public static final String DEFAULT_BUILT = "BUILT";
        public static final String DEFAULT_RELEASED = "RELEASED";
    }
    
    
    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private String name;
    private String pvob;
    private Component component;
    private PromotionLevel promotionLevel = PromotionLevel.NONE;

    private boolean created = false;
    
    /*******************************
     **** CONSTRUCTORS *************
     *******************************/

    public Baseline(String nameWithPvob) {
        if (nameWithPvob.contains("@")) {
            String[] tab = nameWithPvob.split("@");
            this.name = tab[0];
            this.pvob = tab[1];
        } else {
            this.name = nameWithPvob;
        }
    }
    
    public Baseline(String nameWithPvob, Component component) {
        this(nameWithPvob);
        this.component = component;
    }  
    
    public Baseline(String name, String pvob) {
        this.name = name;
        this.pvob = pvob;
    }
    
    public Baseline(String name, String pvob, Component component) {
        this.name = name;
        this.pvob = pvob;
        this.component = component;
    }
    
    /*******************************
     **** METHODS ******************
     *******************************/
    
    @Override
    public boolean equals(Object obj) {
        try {
            Baseline other = (Baseline) obj;
            return this.name.equals(other.name);
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

    public Component getComponent() {
        return component;
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }

    public boolean isCreated() {
        return created;
    }
    public PromotionLevel getPromotionLevel() {
        return promotionLevel;
    }

    public void setPromotionLevel(PromotionLevel promotionLevel) {
        this.promotionLevel = promotionLevel;
    }
}
