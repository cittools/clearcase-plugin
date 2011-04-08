package hudson.plugins.clearcase.objects;

import hudson.model.User;
import hudson.plugins.clearcase.util.Tools;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public abstract class ClearCaseChangeLogEntry extends ChangeLogSet.Entry {
    
    public static final DateFormat OUTPUT_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    
    /**************
     *** FIELDS ***
     **************/
    protected Date date;
    protected String dateStr;
    protected String user = "";
    protected List<AffectedFile> files = new ArrayList<AffectedFile>();
    
    /*******************
     *** CONSTRUCTOR ***
     *******************/
    
    public ClearCaseChangeLogEntry() {
        super();
    }
    
    /**
     * Copy contructor
     */
    public ClearCaseChangeLogEntry(ClearCaseChangeLogEntry other) {
        super();
        this.setUser(other.user);
        this.setDate(other.date);
        this.setParent(other.getParent());
        for (AffectedFile f : other.files) {
            this.files.add(f);
        }
    }
    
    /****************
     *** OVERRIDE ***
     ****************/

    /** override from {@link ChangeLogSet.Entry#getAffectedFiles()} */
    @Override
    public Collection<AffectedFile> getAffectedFiles() {
        return this.files;
    }

    /** override from {@link ChangeLogSet.Entry#getAffectedPaths()} */
    @Override
    public Collection<String> getAffectedPaths() {
        List<String> paths = new ArrayList<String>();
        for (AffectedFile file : this.files) {
            paths.add(file.getPath());
        }
        return paths;
    }

    /** override from {@link ChangeLogSet.Entry#getAuthor()} */
    @Override
    public User getAuthor() {
        return User.get(this.user);
    }
    
    /**
     * This is a hack to make {@link Entry#setParent()} visible to public.
     */
    public void setCustomParent(ChangeLogSet<? extends ChangeLogSet.Entry> parent) {
        super.setParent(parent);
    }
    

    /***********************
     *** UTILITY METHODS ***
     ***********************/
    
    public void addFile(AffectedFile file) {
        this.files.add(file);
    }
    
    /*************************
     *** GETTERS & SETTERS ***
     *************************/
    
    public String getUser() {
        return this.user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<AffectedFile> getFiles() {
        return this.files;
    }

    public void setFiles(List<AffectedFile> files) {
        this.files = files;
    }
    
    public boolean hasFiles() {
        return !this.files.isEmpty();
    }
    
    public Date getDate() {
        if (this.date == null) {
            try {
                this.date = Tools.parseDate(dateStr);
            } catch (Exception e) {
                /* pass */
            }
        }
        return date;
    }
    
    public void setDate(Date date) {
        this.date = date;
    }
    
    public String getDateStr() {
        if (date != null) {
            return OUTPUT_FORMAT.format(date);
        } else {
            return dateStr;
        }
    }
    
    public void setDateStr(String dateStr) {
        try {
            this.date = Tools.parseDate(dateStr);
        } catch (Exception e) {
            /* pass */
        }
        this.dateStr = dateStr;
    }
    

    
    
}
