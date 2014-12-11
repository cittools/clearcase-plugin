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
package hudson.plugins.clearcase.changelog;

import static org.apache.commons.lang.StringEscapeUtils.escapeXml;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.objects.UcmActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.kohsuke.stapler.export.Exported;

/**
 * UCM ClearCase change log set.
 * 
 * @author Henrik L. Hansen
 */
public class UcmChangeLogSet extends ClearCaseChangeLogSet<UcmActivity> {

    private List<UcmActivity> history = null;

    public UcmChangeLogSet(AbstractBuild<?, ?> build) {
        this(build, new ArrayList<UcmActivity>());
    }
    
    public UcmChangeLogSet(AbstractBuild<?, ?> build, List<UcmActivity> logs) {
        super(build);
        for (UcmActivity entry : logs) {
            entry.setCustomParent(this);
        }
        this.history = logs;
    }

    @Override
    public boolean isEmptySet() {
        return history.isEmpty();
    }

    public Iterator<UcmActivity> iterator() {
        return history.iterator();
    }

    @Override
    @Exported
    public List<UcmActivity> getLogs() {
        return history;
    }

    @Override
    public void saveToFile(File changeLogFile) throws IOException {

        FileOutputStream fileOutputStream = new FileOutputStream(changeLogFile);

        PrintStream stream = new PrintStream(fileOutputStream, false, "UTF-8");

        stream.println("<?xml version='1.0' encoding='UTF-8'?>");
        stream.println("<history>");
        for (UcmActivity activity : history) {
            stream.println("\t<entry>");
            stream.println("\t\t<name>" + escapeXml(activity.getName()) + "</name>");
            stream.println("\t\t<headline>" + escapeXml(activity.getHeadline()) + "</headline>");
            stream.println("\t\t<stream>" + escapeXml(activity.getStream()) + "</stream>");
            stream.println("\t\t<user>" + escapeXml(activity.getUser()) + "</user>");
            for (UcmActivity subActivity : activity.getSubActivities()) {
                printSubActivity(stream, subActivity);
            }
            for (hudson.plugins.clearcase.objects.AffectedFile file : activity.getFiles()) {
                printFile(stream, file);
            }
            stream.println("\t</entry>");
        }
        stream.println("</history>");

        stream.close();
        fileOutputStream.close();
    }

    protected void printFile(PrintStream stream, hudson.plugins.clearcase.objects.AffectedFile file) {
        stream.println("\t\t<file>");
        stream.println("\t\t\t<name>" + escapeXml(file.getName()) + "</name>");
        stream.println("\t\t\t<date>" + escapeXml(file.getDateStr()) + "</date>");
        stream.println("\t\t\t<comment>" + escapeXml(file.getComment()) + "</comment>");
        stream.println("\t\t\t<version>" + escapeXml(file.getVersion()) + "</version>");
        stream.println("\t\t\t<event>" + escapeXml(file.getEvent()) + "</event>");
        stream.println("\t\t\t<operation>" + escapeXml(file.getOperation()) + "</operation>");
        stream.println("\t\t</file>");
    }

    protected void printSubActivity(PrintStream stream, UcmActivity activity) {
        stream.println("\t\t<subactivity>");
        stream.println("\t\t\t<name>" + escapeXml(activity.getName()) + "</name>");
        stream.println("\t\t\t<headline>" + escapeXml(activity.getHeadline()) + "</headline>");
        stream.println("\t\t\t<stream>" + escapeXml(activity.getStream()) + "</stream>");
        stream.println("\t\t\t<user>" + escapeXml(activity.getUser()) + "</user>");
        for (UcmActivity subActivity : activity.getSubActivities()) {
            printSubActivity(stream, subActivity);
        }
        for (hudson.plugins.clearcase.objects.AffectedFile file : activity.getFiles()) {
            printFile(stream, file);
        }
        stream.println("\t\t</subactivity>");
    }
}
