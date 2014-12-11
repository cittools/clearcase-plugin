package hudson.plugins.clearcase.objects;

import hudson.plugins.clearcase.util.Tools;
import hudson.scm.EditType;

import java.util.Date;

public class AffectedFile implements hudson.scm.ChangeLogSet.AffectedFile {

    /**************
     *** FIELDS ***
     **************/
    
    private String name = "";
    private Date date = null;
    private String dateStr = "";
    private String version = "";
    private String action = "";
    private String operation = "checkin";
    private String event = "";
    private String comment = "";
    
    /********************
     *** CONSTRUCTORS ***
     ********************/
    public AffectedFile() {
    }

    /****************
     *** OVERRIDE ***
     ****************/
    
    @Override
    public EditType getEditType() {
        if (this.operation.equalsIgnoreCase("mkelem")) {
            return EditType.ADD;
        } else if (this.operation.equalsIgnoreCase("rmelem")) {
            return EditType.DELETE;
        } else if (this.operation.equalsIgnoreCase("checkin")) {
            return EditType.EDIT;
        }
        return null;
    }

    @Override
    public String getPath() {
        return this.name;
    }
    
    /*************************
     *** GETTERS & SETTERS ***
     *************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
            return ClearCaseChangeLogEntry.OUTPUT_FORMAT.format(date);
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
    
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
