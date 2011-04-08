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
package hudson.plugins.clearcase.history;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.UcmChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.cleartool.HistoryFormatHandler;
import hudson.plugins.clearcase.objects.AffectedFile;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
/**
 *
 * @author hlyh
 */
public class UcmHistoryAction extends HistoryAction {

    public static final String[] HISTORY_FORMAT = {
        HistoryFormatHandler.DATE_NUMERIC,
        HistoryFormatHandler.USER_ID,
        HistoryFormatHandler.NAME_ELEMENTNAME,
        HistoryFormatHandler.NAME_VERSIONID,
        HistoryFormatHandler.EVENT,
        HistoryFormatHandler.OPERATION,
        HistoryFormatHandler.UCM_VERSION_ACTIVITY
    };

    public static final String[] ACTIVITY_FORMAT = {
        HistoryFormatHandler.UCM_ACTIVITY_HEADLINE,
        HistoryFormatHandler.UCM_ACTIVITY_STREAM,
        HistoryFormatHandler.USER_ID,
        HistoryFormatHandler.UCM_ACTIVITY_CONTRIBUTING,
        HistoryFormatHandler.COMMENT_NONEWLINE
    };


    private HistoryFormatHandler historyHandler = new HistoryFormatHandler(HISTORY_FORMAT);

    public UcmHistoryAction(ClearTool cleartool, List<Filter> filters) {
        super(cleartool, filters);
    }

    
    @Override
    public HistoryFormatHandler getHistoryFormatHandler() {
        return historyHandler;
    }

    @Override
    protected 
    UcmChangeLogSet 
    buildChangelog(AbstractBuild<?, ?> build, View view, List<HistoryEntry> entries) 
    throws IOException, InterruptedException
    {
        List<UcmActivity> activities = new ArrayList<UcmActivity>();
        Map<String,UcmActivity> activityMap = new HashMap<String, UcmActivity>();

        for (HistoryEntry entry : entries) {

            UcmActivity activity = activityMap.get(entry.getActivityName());
            if (activity == null) {
                activity = new UcmActivity();
                activity.setDate(entry.getDate());
                activity.setHeadline(entry.getActivityHeadline());
                activity.setName(entry.getActivityName());
                activity.setUser(entry.getUser());
                activityMap.put(entry.getActivityName(), activity);
                activities.add(activity);
            }

            AffectedFile currentFile = new AffectedFile();
            currentFile.setComment(entry.getComment());
            currentFile.setDate(entry.getDate());
            currentFile.setDateStr(entry.getDateText());
            currentFile.setEvent(entry.getEvent());
            currentFile.setName(entry.getElement());
            currentFile.setOperation(entry.getOperation());
            currentFile.setVersion(entry.getVersionId());
            activity.getFiles().add(currentFile);
        }

        try {
            for (UcmActivity activity : activities) {
                int recursionDepth = 3;
                callLsActivity(activityMap, activity, view, recursionDepth);
            }
        } catch (ClearToolError e) {
            /* empty by design */
        }

        return new UcmChangeLogSet(build, activities);
    }

    private void callLsActivity(Map<String,UcmActivity> activityMap, UcmActivity activity,
                                View view, int recursionDepth) 
            throws IOException, InterruptedException, ClearToolError 
    {
        HistoryFormatHandler handler = new HistoryFormatHandler(ACTIVITY_FORMAT);
        UcmActivity act = cleartool.lsactivity(activity.getName(), handler, view);
        
        if (act != null) {
            activity.setHeadline(act.getHeadline());
            activity.setStream(act.getStream());
            activity.setUser(act.getUser());
            activity.setComment(act.getComment());
            
            if (activity.isIntegrationActivity() && recursionDepth > 0) {
                
                for (String contributing : act.getContribActivitiesStr().split(" ")) {
                    UcmActivity subActivity = null;
                    UcmActivity cachedActivity = activityMap.get(contributing);
                    
                    if (cachedActivity ==null) {
                        subActivity = new UcmActivity();
                        subActivity.setName(contributing);
                        callLsActivity(activityMap, subActivity, view, --recursionDepth);
                        activityMap.put(contributing, subActivity);
                    } else {
                        /* do deep copy */
                        subActivity = new UcmActivity(cachedActivity);
                    }
                    
                    activity.addSubActivity(subActivity);
                }
            }
        }

    }


}
