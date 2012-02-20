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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.clearcase.history;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.changelog.ClearCaseChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.cleartool.HistoryFormatHandler;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogSet;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author hlyh
 */
public abstract class HistoryAction {

    protected ClearTool cleartool;
    protected List<Filter> filters;
    protected String extendedViewPath;

    public HistoryAction(ClearTool cleartool, List<Filter> filters) {
        this.cleartool = cleartool;
        this.filters = filters != null ? filters : new ArrayList<Filter>();
    }
    
    /**
     * Returns if the repository has any changes since the specified time
     * @param time check for changes since this time
     * @param view the name of the view
     * @param branchNames the branch names
     * @param viewPaths optional vob paths
     * @return List of changes
     * @throws ClearToolError 
     */
    public ClearCaseChangeLogSet<? extends ChangeLogSet.Entry> getChanges(AbstractBuild<?, ?> build, 
            Date time, View view, List<String> branchNames, List<String> viewPaths) 
            throws IOException, InterruptedException, ClearToolError 
    {
        List<HistoryEntry> entries = runLsHistory(time, view, branchNames, viewPaths);
        List<HistoryEntry> filtered = filterEntries(entries);

        return buildChangelog(build, view, filtered);
    }



    /**
     * Returns if the repository has any changes since the specified time
     * @param time check for changes since this time
     * @param view the name of the view
     * @param branchNames the branch names
     * @param viewPaths optional vob paths
     * @return true, if the ClearCase repository has changes; false, otherwise.
     * @throws ClearToolError 
     */
    public boolean pollChanges(Date time, View view, List<String> branchNames, List<String> viewPaths) 
            throws IOException, InterruptedException, ClearToolError 
    {
        for (String branch : branchNames) {
            if (cleartool.hasCheckouts(branch, view, viewPaths)) {
                String message = "There are checkouts in the branch: %s. No build has been triggered.";
                throw new ClearToolError(String.format(message, branch));
            }
        }
        List<HistoryEntry> entries = runLsHistory(time, view, branchNames, viewPaths); 
        List<HistoryEntry> filtered = filterEntries(entries);

        return filtered.size() > 0;
    }
    
    
    
    protected List<HistoryEntry> runLsHistory(Date time, View view, List<String> branchNames,
            List<String> viewPaths) 
            throws IOException, InterruptedException, ClearToolError 
    {
        HistoryFormatHandler historyHandler = getHistoryFormatHandler();
        List<HistoryEntry> fullList = new ArrayList<HistoryEntry>();

        try {
            for (String branchName : branchNames) {
                fullList.addAll(cleartool.lshistory(historyHandler, time, view, branchName, 
                                viewPaths, extendedViewPath));
            }
        } catch (ParseException ex) {
            /* empty by design */
        }
        return fullList;
    }

    protected List<HistoryEntry> filterEntries(List<HistoryEntry> unfiltered) throws IOException,
            InterruptedException {
        List<HistoryEntry> filtered = new ArrayList<HistoryEntry>();

        for (HistoryEntry entry : unfiltered) {
            boolean accepted = true;
            for (Filter filter : filters) {
                accepted = accepted && filter.accept(entry);
            }
            if (accepted) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    protected abstract 
    ClearCaseChangeLogSet<? extends ChangeLogSet.Entry> 
    buildChangelog(AbstractBuild<?, ?> build, View view, List<HistoryEntry> entries) 
    throws IOException, InterruptedException;

    public abstract 
    HistoryFormatHandler 
    getHistoryFormatHandler();

    /**
     * Sets the extended view path. The extended view path will be removed from
     * file paths in the event. The extended view path is for example the view
     * root + view name; and this path shows up in the history and can be
     * conusing for users.
     * 
     * @param path
     *            the new extended view path.
     */
    public void setExtendedViewPath(String path) {
    this.extendedViewPath = path;
    }

    public String getExtendedViewPath() {
        return extendedViewPath;
    }
}
