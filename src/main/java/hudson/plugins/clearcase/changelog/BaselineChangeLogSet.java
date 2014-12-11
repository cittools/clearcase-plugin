package hudson.plugins.clearcase.changelog;

import static org.apache.commons.lang.StringEscapeUtils.escapeXml;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.UcmActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public class BaselineChangeLogSet extends UcmChangeLogSet {

    private Baseline baseline;

    public BaselineChangeLogSet(AbstractBuild<?, ?> build) {
        super(build);
    }

    public BaselineChangeLogSet(AbstractBuild<?, ?> build, Baseline baseline, List<UcmActivity> logs)
    {
        super(build, logs);
        this.baseline = baseline;
    }

    public void addActivity(UcmActivity activity) {
        getLogs().add(activity);
    }
    
    public Baseline getBaseline() {
        return baseline;
    }

    public void setBaseline(Baseline baseline) {
        this.baseline = baseline;
    }

    @Override
    public void saveToFile(File changeLogFile) throws IOException {

        FileOutputStream fileOutputStream = new FileOutputStream(changeLogFile);

        PrintStream stream = new PrintStream(fileOutputStream, false, "UTF-8");

        stream.println("<?xml version='1.0' encoding='UTF-8'?>");
        stream.println("<history>");
        stream.println("\t<baseline>");
        stream.println("\t\t<name>" + baseline.getName() + "</name>" );
        stream.println("\t\t<pvob>" + baseline.getPvob() + "</pvob>" );
        stream.println("\t\t<stream>" + baseline.getStream() + "</stream>" );
        stream.println("\t</baseline>");
        for (UcmActivity activity : getLogs()) {
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
    
}
