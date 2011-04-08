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
import hudson.plugins.clearcase.changelog.BaseChangeLogSet;
import hudson.plugins.clearcase.changelog.ClearCaseChangeLogSet;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.cleartool.HistoryFormatHandler;
import hudson.plugins.clearcase.objects.AffectedFile;
import hudson.plugins.clearcase.objects.BaseChangeLogEntry;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.View;
import hudson.scm.ChangeLogSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author hlyh
 */
public class BaseHistoryAction extends HistoryAction {

    public static final String[] HISTORY_FORMAT = {
        HistoryFormatHandler.DATE_NUMERIC,
        HistoryFormatHandler.USER_ID,        
        HistoryFormatHandler.NAME_ELEMENTNAME,
        HistoryFormatHandler.NAME_VERSIONID,
        HistoryFormatHandler.EVENT,
        HistoryFormatHandler.OPERATION
    };

    private HistoryFormatHandler historyHandler = new HistoryFormatHandler(HISTORY_FORMAT);
    private int maxTimeDifferenceMillis;

    public BaseHistoryAction(ClearTool cleartool, List<Filter> filters, int maxTimeDifferenceMillis)
    {
        super(cleartool, filters);
        this.maxTimeDifferenceMillis = maxTimeDifferenceMillis;
    }

    @Override
    protected  
    ClearCaseChangeLogSet<? extends ChangeLogSet.Entry> 
    buildChangelog(AbstractBuild<?, ?> build, View view, List<HistoryEntry> entries) 
    throws IOException, InterruptedException
    {
        List<BaseChangeLogEntry> fullList = new ArrayList<BaseChangeLogEntry>();

        for (HistoryEntry entry : entries) {
            BaseChangeLogEntry changelogEntry = new BaseChangeLogEntry();

            changelogEntry.setDate(entry.getDate());
            changelogEntry.setUser(entry.getUser());
            changelogEntry.setComment(entry.getComment());

            AffectedFile file = new AffectedFile();
            file.setName(entry.getElement());
            file.setVersion(entry.getVersionId());
            file.setEvent(entry.getEvent());
            file.setOperation(entry.getOperation());

            changelogEntry.getFiles().add(file);
            fullList.add(changelogEntry);
        }
        EntryMerger entryMerger = new EntryMerger(maxTimeDifferenceMillis);
        List<BaseChangeLogEntry> merged = entryMerger.getMergedList(fullList);

        return new BaseChangeLogSet(build, merged);
    }

    @Override
    public HistoryFormatHandler getHistoryFormatHandler() {
        return historyHandler;
    }
}
