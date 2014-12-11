package hudson.plugins.clearcase.changelog;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.objects.AffectedFile;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.UcmActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

public class BaselineChangeLogParser extends UcmChangeLogParser {

    @Override
    public BaselineChangeLogSet parse(@SuppressWarnings("rawtypes") AbstractBuild build,
            File changeLogFile) throws IOException, SAXException
    {
        FileInputStream fileInputStream = new FileInputStream(changeLogFile);

        // Parse the change log file.
        Digester digester = new Digester();
        digester.setClassLoader(BaselineChangeLogSet.class.getClassLoader());

        BaselineChangeLogSet changelog = new BaselineChangeLogSet(build);
        digester.push(changelog);
        
        digester.addObjectCreate("*/baseline", Baseline.class);
        digester.addBeanPropertySetter("*/baseline/name");
        digester.addBeanPropertySetter("*/baseline/pvob");
        digester.addObjectCreate("*/baseline/stream", Stream.class);
        digester.addBeanPropertySetter("*/baseline/stream/name");
        digester.addBeanPropertySetter("*/baseline/stream/pvob");
        digester.addSetNext("*/baseline/stream", "setStream");
        digester.addSetNext("*/baseline", "setBaseline");
        
        digester.addObjectCreate("*/entry", UcmActivity.class);
        digester.addBeanPropertySetter("*/entry/name");
        digester.addBeanPropertySetter("*/entry/headline");
        digester.addBeanPropertySetter("*/entry/stream");
        digester.addBeanPropertySetter("*/entry/user");

        digester.addObjectCreate("*/subactivity", UcmActivity.class);
        digester.addBeanPropertySetter("*/subactivity/name");
        digester.addBeanPropertySetter("*/subactivity/headline");
        digester.addBeanPropertySetter("*/subactivity/stream");
        digester.addBeanPropertySetter("*/subactivity/user");
        digester.addSetNext("*/subactivity", "addSubActivity");

        digester.addObjectCreate("*/entry/file", AffectedFile.class);
        digester.addBeanPropertySetter("*/entry/file/name");
        digester.addBeanPropertySetter("*/entry/file/date", "dateStr");
        digester.addBeanPropertySetter("*/entry/file/comment");
        digester.addBeanPropertySetter("*/entry/file/version");
        digester.addBeanPropertySetter("*/entry/file/event");
        digester.addBeanPropertySetter("*/entry/file/operation");
        digester.addSetNext("*/entry/file", "addFile");
        digester.addSetNext("*/entry", "addActivity");

        digester.parse(fileInputStream);
        fileInputStream.close();

        return changelog;
    }
}
