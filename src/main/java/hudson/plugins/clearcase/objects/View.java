package hudson.plugins.clearcase.objects;

public class View {

    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private String name;
    private Stream stream;
    private String viewPath;
    private String localPath;
    private String globalPath;
    private String uuid;
    
    private boolean dynamic = false;
    private boolean ucm = false;
 
    /*******************************
     **** CONSTRUCTORS *************
     *******************************/
    
    public View(String name) {
        this.name = name;
    }
    
    public View(String name, boolean dynamic) {
        this.name = name;
        this.dynamic = dynamic;
    }
    
    public View(String name, Stream stream) {
        this(name);
        this.stream = stream;
        this.ucm = true;
    }
    
    public View(String name, Stream stream, boolean dynamic) {
        this(name, stream);
        this.dynamic = dynamic;
    }
    
    public View(View view) {
        this.name = view.name;
        this.stream = view.stream;
        this.viewPath = view.viewPath;
        this.localPath = view.localPath;
        this.globalPath = view.globalPath;
        this.uuid = view.uuid;
        this.dynamic = view.dynamic;
        this.ucm = view.ucm;
    }
    
    /*******************************
     **** METHODS ******************
     *******************************/

    @Override
    public String toString() {
        return this.name;
    }
    
    @Override
    public boolean equals(Object obj) {
        try {
            View other = (View) obj;
            return this.name.equals(other.name);
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

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
        this.ucm = true;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public boolean isUcm() {
        return ucm;
    }
    
    public void setUcm(boolean ucm) {
        this.ucm = ucm;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid.replaceAll("[\\.:]", "").trim();
    }

    public String getUuid() {
        return uuid;
    }
    
    public String getGlobalPath() {
        return globalPath;
    }

    public void setGlobalPath(String globalPath) {
        this.globalPath = globalPath;
    }
    
    public String getViewPath() {
        return viewPath;
    }

    public void setViewPath(String viewPath) {
        this.viewPath = viewPath;
    }
}
