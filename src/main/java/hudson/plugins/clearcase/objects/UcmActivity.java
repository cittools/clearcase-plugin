/**
 * The MIT License
 *
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.clearcase.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Changelog entry for UCM ClearCase
 *
 * @author Henrik L. Hansen
 */
public class UcmActivity extends ClearCaseChangeLogEntry {

    /**************
     *** FIELDS ***
     **************/
    
    private String name;
    private String headline;
    private String stream;
    private String comment = "";
    private String contribActivitiesStr = "";

    private List<UcmActivity> subActivities = new ArrayList<UcmActivity>();

    /********************
     *** CONSTRUCTORS ***
     ********************/
    
    public UcmActivity() {
        super();
    }

    /**
     * Copy contructor
     */
    public UcmActivity(UcmActivity other) {
        super(other);
        this.name = other.name;
        this.headline = other.headline;
        this.stream = other.stream;
        this.comment = other.comment;
        this.contribActivitiesStr = other.contribActivitiesStr;
        for (UcmActivity subAct : other.getSubActivities()) {
            addSubActivity(new UcmActivity(subAct));
        }
    }

    /****************
     *** OVERRIDE ***
     ****************/
    
    @Override
    public String getMsg() {
        if (this.isIntegrationActivity()) {
            if (this.headline == null) {
                return (this.name != null) ? this.name : "unknown activity";
            } else {
                return this.headline;
            }
        } else {
            if (this.headline == null || this.name == null) {
                return (this.name != null) ? this.name : "unknown activity";
            } else {
                return this.name + " - " + this.headline;
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }

    /***********************
     *** UTILITY METHODS ***
     ***********************/
    
    public boolean isIntegrationActivity() {
        if (comment.startsWith("Integration activity created by deliver")) {
            return true;
        } else {
            return false;
        }        
    }
    
    public void addSubActivity(UcmActivity activity) {
        subActivities.add(activity);
    }

    public void addSubActivities(Collection<UcmActivity> activities) {
        this.subActivities.addAll(activities);
    }
    
    public boolean hasSubActivities() {
        return !this.subActivities.isEmpty();
    }
    
    /*************************
     *** GETTERS & SETTERS ***
     *************************/
    
    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public List<UcmActivity> getSubActivities() {
        return subActivities;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }
    public String getContribActivitiesStr() {
        return contribActivitiesStr;
    }

    public void setContribActivitiesStr(String contribActivitiesStr) {
        this.contribActivitiesStr = contribActivitiesStr;
    }

}
