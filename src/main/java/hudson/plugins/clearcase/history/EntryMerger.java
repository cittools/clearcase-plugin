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

import hudson.plugins.clearcase.objects.BaseChangeLogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class that merges clearcase log entries considering the time window specified 
 * in the global configuration. 
 */
public class EntryMerger {

    /**************
     *** FIELDS ***
     **************/
    
    private Map<String, List<MergedEntry>> userEntries = new HashMap<String, List<MergedEntry>>();
    private transient int maxTimeDifference;

    /********************
     *** CONSTRUCTORS ***
     ********************/
    
    public EntryMerger() {
        this(0);
    }

    public EntryMerger(int maxTimeDifferenceMillis) {
        this.maxTimeDifference = maxTimeDifferenceMillis + 1000;
    }

    /***************
     *** METHODS ***
     ***************/
    
    /**
     * Merges clearcase log entries considering the time window specified 
     * in the global configuration. 
     */
    public List<BaseChangeLogEntry> getMergedList(List<BaseChangeLogEntry> orgList) {
        userEntries.clear();
        for (BaseChangeLogEntry entry : orgList) {
            boolean wasMerged = false;
            List<MergedEntry> entries = getUserEntries(entry.getUser());
            for (MergedEntry storedEntry : entries) {
                if (canBeMerged(storedEntry, entry)) {
                    storedEntry.merge(entry);
                    wasMerged = true;
                    break;
                }
            }
            if (!wasMerged) {
                entries.add(new MergedEntry(entry));
            }
        }
        List<BaseChangeLogEntry> list = getList();
        Collections.sort(list, new Comparator<BaseChangeLogEntry>() {
            public int compare(BaseChangeLogEntry o1, BaseChangeLogEntry o2) {
                return o2.getDate().compareTo(o1.getDate());
            }
        });
        return list;
    }

    /**
     * Get all entries.
     */
    private List<BaseChangeLogEntry> getList() {
        List<BaseChangeLogEntry> list = new ArrayList<BaseChangeLogEntry>();
        Set<String> users = userEntries.keySet();
        for (String user : users) {
            List<MergedEntry> userList = userEntries.get(user);
            for (MergedEntry entry : userList) {
                entry.entry.setDate(entry.oldest);
                list.add(entry.entry);
            }
        }
        return list;
    }

    /**
     * Get all entries from one user.
     */
    private List<MergedEntry> getUserEntries(String user) {
        if (!userEntries.containsKey(user)) {
            userEntries.put(user, new ArrayList<MergedEntry>());
        }
        return userEntries.get(user);
    }

    /**
     * If the two entries have the same comment, we check if they were created 
     * in the same time window (as specified in the global configuration)
     */
    private boolean canBeMerged(MergedEntry entryOne, BaseChangeLogEntry entryTwo) {
        if (entryOne.entry.getComment().equals(entryTwo.getComment())) {
            long oldestDiff = Math.abs(entryOne.oldest.getTime() - entryTwo.getDate().getTime());
            long newestDiff = Math.abs(entryOne.newest.getTime() - entryTwo.getDate().getTime());
            return (oldestDiff < maxTimeDifference) || (newestDiff < maxTimeDifference);
        }
        return false;
    }
    
    
    /**
     * Utility class to represent merged entries.
     */
    private class MergedEntry {
        
        /**************
         *** FIELDS ***
         **************/
        
        private BaseChangeLogEntry entry;
        private Date oldest;
        private Date newest;
        
        /*******************
         *** CONSTRUCTOR ***
         *******************/
        
        public MergedEntry(BaseChangeLogEntry entry) {
            this.entry = entry;
            oldest = entry.getDate();
            newest = entry.getDate();
        }
        
        /***************
         *** METHODS ***
         ***************/
        
        public void merge(BaseChangeLogEntry newEntry) {
            if (newEntry.getDate().after(newest)) {
                newest = newEntry.getDate();
            } else {
                oldest = newEntry.getDate();
            }
            entry.getAffectedFiles().addAll(newEntry.getAffectedFiles());
        }
    }
}
