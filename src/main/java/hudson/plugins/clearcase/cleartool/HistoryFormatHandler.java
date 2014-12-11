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
package hudson.plugins.clearcase.cleartool;

import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.UcmActivity;

import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author hlyh
 */
public class HistoryFormatHandler {

    private String format;
    private String patternStr;
    private int groupCount;
    private Pattern pattern;

    public HistoryFormatHandler(String... elements) {
        setPattern(elements);
    }

    public void setPattern(String... elements) {
        StringBuilder formatBuilder = new StringBuilder();
        StringBuilder patternBuilder = new StringBuilder();
        for (String element : elements) {
            formatBuilder.append(START_DELIMITER);
            formatBuilder.append(element);
            formatBuilder.append(END_DELIMITER);
            patternBuilder.append(REGEX_GROUP);
        }
        formatBuilder.append(LINEEND);
        groupCount = elements.length;
        format = formatBuilder.toString();
        patternStr = patternBuilder.toString();
        pattern = Pattern.compile(patternStr);
    }

    public String getFormat() {
        return format;
    }

    public String getPattern() {
        return patternStr;
    }

    public Matcher checkLine(String line) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.find() && matcher.groupCount() == groupCount) {
            return matcher;
        }
        return null;
    }
    
    public HistoryEntry parseHistoryLine(Matcher matcher, String line) throws ParseException {
        HistoryEntry entry = new HistoryEntry();
        entry.setLine(line);

        entry.setDate(HistoryEntry.LSHISTORY_DATE_FORMAT.parse(matcher.group(1)));
        entry.setUser(matcher.group(2).trim());
        entry.setElement(matcher.group(3).trim());
        entry.setVersionId(matcher.group(4).trim());
        entry.setEvent(matcher.group(5).trim());
        entry.setOperation(matcher.group(6).trim());
        if (groupCount == 7) {
            entry.setActivityName(matcher.group(7).trim());
        }
        return entry;
    }
    
    public UcmActivity parseActivity(String result) {
        Matcher matcher = pattern.matcher(result);
        if (matcher.find() && matcher.groupCount() == groupCount) {
            UcmActivity activity = new UcmActivity();
            activity.setHeadline(matcher.group(1).trim());
            activity.setStream(matcher.group(2).trim());
            activity.setUser(matcher.group(3).trim());
            activity.setContribActivitiesStr(matcher.group(4).trim());
            activity.setComment(matcher.group(5).trim());
            activity.setName(matcher.group(6).trim());
            return activity;
        } else {
            return null;
        }
    }

    // Comment
    public static final String COMMENT = "%c";
    public static final String COMMENT_NONEWLINE = "%Nc";
    // Date
    public static final String DATE = "%d";
    public static final String DATE_NUMERIC = "%Nd";
    // Event
    public static final String EVENT = "%e";
    public static final String LINEEND = "\\n";
    // Name
    public static final String NAME = "%n";
    public static final String NAME_ELEMENTNAME = "%En";
    public static final String NAME_VERSIONID = "%Vn";
    // Event
    public static final String OPERATION = "%o";
    public static final String PLACEHOLDER = "\\\" \\\" ";
    public static final String REGEX_GROUP = "\"(.*)\"\\s*";
    // Format
    public static final String START_DELIMITER = "\\\"";
    public static final String END_DELIMITER = "\\\" "; // Note the space!
    // UCM Activities
    public static final String UCM_ACTIVITY_HEADLINE = "%[headline]p";
    public static final String UCM_ACTIVITY_STREAM = "%[stream]p";
    public static final String UCM_ACTIVITY_VIEW = "%[view]p";
    public static final String UCM_ACTIVITY_CONTRIBUTING = "%[contrib_acts]p";
    public static final String UCM_ACTIVITY_VERSIONS = "%[versions]CQp";
    public static final String UCM_VERSION_ACTIVITY = "%[activity]p";
    
    // UCM Versions
    public static final String USER_FULLNAME = "%Fu";
    public static final String USER_GROUPNAME = "%Gu";
    // User
    public static final String USER_ID = "%u";
    public static final String USER_LOGIN_AND_GROUP = "%Lu";
}
