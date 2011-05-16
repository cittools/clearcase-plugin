package hudson.plugins.clearcase.log;

import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.util.FlushProofOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;

import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ClearToolLogFile implements Serializable {

    /*******************************
     **** FIELDS *******************
     *******************************/
    private static final long serialVersionUID = -6662926481538538623L;
    
    /*******************************
     **** CONSTRUCTOR **************
     *******************************/
    public ClearToolLogFile() {
    }

    
    public Object getDynamic(final String link, final StaplerRequest request, 
            final StaplerResponse response) throws IOException 
    {
        response.sendRedirect2("index");
        return null;
    }
    
    /*****************
     **** GETTERS ****
     *****************/
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public AnnotatedLargeText getLogText(AbstractBuild<?, ?> build) {
        return new AnnotatedLargeText(getLogFile(build), Charset.defaultCharset(),
                                      !build.isLogUpdated(), ClearToolAnnotator.class);
    }
    
    public void writeLogTo(AbstractBuild<?, ?> build, long offset, XMLOutput out) throws IOException {
        getLogText(build).writeHtmlTo(offset, out.asWriter());
    }
    
    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setAttribute("html",true);
        AbstractBuild<?, ?> build = req.findAncestorObject(hudson.model.AbstractBuild.class);
        getLogText(build).doProgressText(req,rsp);
    }
    
    /**
     * Sends out the raw console output.
     */
    public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        // Prevent jelly from flushing stream so Content-Length header can be added afterwards
        FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
        AbstractBuild<?, ?> build = req.findAncestorObject(hudson.model.AbstractBuild.class);
        getLogText(build).writeLogTo(0, out);
        out.close();
    }
    
    public void doBuildStatus( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        AbstractBuild<?, ?> build = req.findAncestorObject(hudson.model.AbstractBuild.class);
        rsp.sendRedirect2(req.getContextPath()+"/images/48x48/"+build.getBuildStatusUrl());
    }
    
    public File getLogFile(AbstractBuild<?, ?> build) {
        return new File(build.getRootDir(), "cleartool.log");
    }
    
    
    public static File getCleartoolLogFile(AbstractBuild<?, ?> build) throws IOException {
        File ctLog = new File(build.getRootDir(), "cleartool.log");
        if (!ctLog.exists()) {
            ctLog.createNewFile();
        }
        return ctLog;
    }
}
