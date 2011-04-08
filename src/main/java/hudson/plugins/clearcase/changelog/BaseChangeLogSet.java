package hudson.plugins.clearcase.changelog;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.objects.BaseChangeLogEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.apache.commons.lang.StringEscapeUtils.escapeXml;

public class BaseChangeLogSet extends ClearCaseChangeLogSet<BaseChangeLogEntry> {

    private List<BaseChangeLogEntry> history = null;

    public BaseChangeLogSet(AbstractBuild<?, ?> build, List<BaseChangeLogEntry> logs) {
        super(build);
        for (BaseChangeLogEntry entry : logs) {
            entry.setCustomParent(this);
        }
        this.history = Collections.unmodifiableList(logs);
    }

    @Override
    public boolean isEmptySet() {
        return history.isEmpty();
    }

    public Iterator<BaseChangeLogEntry> iterator() {
        return history.iterator();
    }

    @Override
    public List<BaseChangeLogEntry> getLogs() {
        return history;
    }

    /**
     * Stores the history objects to the output stream as xml
     * 
     * @param outputStream
     *            the stream to write to
     * @param history
     *            the history objects to store
     * @throws IOException
     */
    @Override
    public void saveToFile(File changeLogFile) throws IOException {
        
        FileOutputStream fileOutputStream = new FileOutputStream(changeLogFile);
        PrintStream stream = new PrintStream(fileOutputStream, false, "UTF-8");

        stream.println("<?xml version='1.0' encoding='UTF-8'?>");
        stream.println("<history>");
        for (BaseChangeLogEntry entry : history) {
            stream.println("\t<entry>");
            stream.println("\t\t<user>" + escapeXml(entry.getUser()) + "</user>");
            stream.println("\t\t<comment>" + escapeXml(entry.getComment()) + "</comment>");
            stream.println("\t\t<date>" + escapeXml(entry.getDateStr()) + "</date>");
            for (hudson.plugins.clearcase.objects.AffectedFile file : entry.getFiles()) {
                printFile(stream, file);
            }
            stream.println("\t</entry>");
        }
        stream.println("</history>");
        stream.close();
        fileOutputStream.close();
    }

    private void printFile(PrintStream stream, hudson.plugins.clearcase.objects.AffectedFile file) {
        stream.println("\t\t<element>");
        stream.println("\t\t\t<file>" + escapeXml(file.getName()) + "</file>");
        stream.println("\t\t\t<action>" + escapeXml(file.getAction()) + "</action>");
        stream.println("\t\t\t<version>" + escapeXml(file.getVersion()) + "</version>");
        stream.println("\t\t\t<operation>" + escapeXml(file.getOperation()) + "</operation>");
        stream.println("\t\t</element>");
    }
    
}
