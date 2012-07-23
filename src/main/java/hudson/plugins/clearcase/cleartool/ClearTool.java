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

import static hudson.plugins.clearcase.cleartool.HistoryFormatHandler.COMMENT;
import static hudson.plugins.clearcase.cleartool.HistoryFormatHandler.LINEEND;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.CompositeComponent;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.Stream.LockState;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.Tools;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ClearTool implements CTFunctions {

    /*******************************
     **** FIELDS *******************
     *******************************/
    private static final Pattern VIEW_INFO_PATTERN = Pattern
            .compile("Global path: ([^\\r\\n]+).*"
                   + "View server access path: ([^\\r\\n]+).*" 
                   + "View uuid: ([^\\r\\n]+)", Pattern.DOTALL);
    private static final Pattern VIEW_ATTRIBUTES_PATTERN = Pattern
            .compile("View attributes: ([^\\r\\n]+)", Pattern.DOTALL);
    private static final Pattern CREATED_BASELINE_PATTERN = Pattern
            .compile("Created baseline \"([^\"]+)\" in component \"([^\"]+)\"");

    protected final CTLauncher launcher;
    private final Map<PromotionLevel, String> promotionLevelNames;

    public static final String REJECTED_PLEVEL_ATTR = "REJECTED_PLEVEL";
    public static final String BUILT_PLEVEL_ATTR = "BUILT_PLEVEL";
    public static final String RELEASED_PLEVEL_ATTR = "RELEASED_PLEVEL";

    /*******************************
     **** CONSTRUCTOR **************
     *******************************/

    public ClearTool(CTLauncher launcher) {
        this.launcher = launcher;

        this.promotionLevelNames = new HashMap<PromotionLevel, String>();

        this.promotionLevelNames.put(PromotionLevel.REJECTED, PromotionLevel.DEFAULT_REJECTED);
        this.promotionLevelNames.put(PromotionLevel.BUILT, PromotionLevel.DEFAULT_BUILT);
        this.promotionLevelNames.put(PromotionLevel.RELEASED, PromotionLevel.DEFAULT_RELEASED);
    }

    /*******************************
     **** ABSTRACT *****************
     *******************************/

    protected abstract FilePath getViewRootPath();

    /*******************************
     **** OVERRIDE *****************
     *******************************/

    /** implements {@link CTFunctions#mkview(View, String, String)} **/
    @Override
    public void mkview(View view, String stgLoc, String optionalMkviewParameters)
            throws IOException, InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        FilePath execPath = null;
        
        args.add("mkview");
        if (!view.isDynamic()) {
            args.add("-snapshot");
            args.add("-ptime"); // preserve the files' creation/modification times
        }
        args.add("-tag", view.getName());

        if (stgLoc != null && !stgLoc.isEmpty()) {
            args.add("-stgloc", stgLoc);
        } else {
            if (view.isDynamic()) {
                throw new ClearToolError("View storage location was not specified. "
                        + "Cannot create a dynamic view.");
            }
        }

        if (view.isUcm() && view.getStream() != null) {
            args.add("-stream", view.getStream().toString());
        }

        args.addTokenized(optionalMkviewParameters);

        if (!view.isDynamic()) {
            // name of the directory where to place view files
            // this is only necessary for snapshot views
            //
            // when creating a dynamic view, the files will be stored in the
            // storage location
            args.add(view.getName());
            execPath = getWorkspace();
        }
        
        launcher.run(args, execPath);
    }

    /**
     * implements
     * {@link CTFunctions#makeBaselines(View, List, boolean, boolean)}
     **/
    @Override
    public List<Baseline> makeBaselines(View view, List<Component> components, boolean identical,
            boolean full, String baseName, String comment) throws IOException,
            InterruptedException, ClearToolError
    {
        FilePath viewPath = null;
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("mkbl");
        if (comment != null) {
            args.add("-comment", comment);
        } else {
            args.add("-nc");
        }
        if (view.isDynamic()) {
            args.add("-view", view.getName());
        } else {
            if (view.getViewPath() != null) {
                /* if viewPath is already defined, we use it */
                viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
            } else {
                /* else, we use a child directory in the workspace/viewRoot */
                viewPath = getViewRootPath().child(view.getName());
            }
        }
        if (components != null && !components.isEmpty()) {
            /* remove all spaces and brackets */
            String compList = components.toString().replaceAll("[\\s\\[\\]]", "");
            args.add("-comp", compList);
        } else {
            args.add("-all");
        }
        if (identical) {
            args.add("-identical");
        }
        if (full) {
            args.add("-full");
        } else {
            args.add("-incremental");
        }
        args.add(baseName);

        String cleartoolResult = launcher.run(args, viewPath);

        List<Baseline> createdBaselines = new ArrayList<Baseline>();

        Matcher matcher = CREATED_BASELINE_PATTERN.matcher(cleartoolResult);

        while (matcher.find()) {
            String baselineName = matcher.group(1);
            String componentName = matcher.group(2);
            Baseline newBaseline = new Baseline(baselineName);

            if (components != null) {
                for (Component c : components) {
                    if (componentName.equals(c.getName())) {
                        newBaseline.setComponent(c);
                        newBaseline.setPvob(c.getPvob());
                        break;
                    }
                }
            }
            createdBaselines.add(newBaseline);
        }

        return createdBaselines;
    }

    /** implements {@link CTFunctions#makeCompositeBaseline()} **/
    @Override
    public List<Baseline> makeCompositeBaseline(View view, CompositeComponent cComp,
            boolean identical, boolean full, String baseName, String comment) throws IOException,
            InterruptedException, ClearToolError
    {
        FilePath viewPath = null;
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("mkbl");

        if (comment != null) {
            args.add("-comment", comment);
        } else {
            args.add("-nc");
        }

        args.add("-comp", cComp.toString());

        if (view.isDynamic()) {
            args.add("-view", view.getName());
        } else {
            if (view.getViewPath() != null) {
                /* if viewPath is already defined, we use it */
                viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
            } else {
                /* else, we use a child directory in the workspace/viewRoot */
                viewPath = getViewRootPath().child(view.getName());
            }
        }

        /* remove all spaces and brackets */
        String compList = cComp.getAttachedComponents().toString().replaceAll("[\\s\\[\\]]", "");
        args.add("-ddepends_on", compList); // break dependencies
        args.add("-adepends_on", compList); // rebuild dependencies

        if (full) {
            args.add("-full");
        } else {
            args.add("-incremental");
        }

        if (identical) {
            args.add("-identical");
        }

        args.add(baseName);

        String cleartoolResult = launcher.run(args, viewPath);

        List<Baseline> createdBaselines = new ArrayList<Baseline>();

        Matcher matcher = CREATED_BASELINE_PATTERN.matcher(cleartoolResult);

        while (matcher.find()) {
            String baselineName = matcher.group(1);
            String componentName = matcher.group(2);
            Baseline newBaseline = new Baseline(baselineName);

            if (componentName.equals(cComp.getName())) {
                newBaseline.setComponent(cComp);
                newBaseline.setPvob(cComp.getPvob());
            } else {
                for (Component c : cComp.getAttachedComponents()) {
                    if (componentName.equals(c.getName())) {
                        newBaseline.setComponent(c);
                        newBaseline.setPvob(c.getPvob());
                        break;
                    }
                }
            }
            createdBaselines.add(newBaseline);
        }

        return createdBaselines;
    }

    @Override
    public List<Baseline> getDependingBaselines(Baseline baseline) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsbl");
        args.add("-fmt", "%[depends_on]p");
        args.add(baseline.toString());

        String result = launcher.run(args, null);

        List<Baseline> dependingBaselines = new ArrayList<Baseline>();

        if (result != null && !result.trim().isEmpty()) {
            String[] bls = result.trim().split("\\s");
            for (String blName : bls) {
                dependingBaselines.add(new Baseline(blName));
            }
        }
        return dependingBaselines;
    }

    /** implements {@link CTFunctions#getLatestBl(Component)} **/
    @Override
    public Baseline getLatestBl(Component comp, Stream stream) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsbl");
        /*
         * the format goes as follows :
         * ",{creation date};baseline:{baseline name}" the comma is for
         * separating baselines from eachother the semicolumn is for separating
         * the creation date from the baseline name
         */
        args.add("-fmt", ",%Nd;%Xn");
        args.add("-comp", comp.toString());
        args.add("-stream", stream.toString());

        String result = launcher.run(args, null);

        if (result != null && result.contains("baseline:")) {
            String[] blNames = result.split(",");
            if (blNames.length > 1) { // the first element will always be an
                                      // empty string
                Arrays.sort(blNames, 0, blNames.length); // we sort the
                                                         // baselines by date
                String latestBlName = blNames[blNames.length - 1]; // we take
                                                                   // the last
                                                                   // occurence
                latestBlName = latestBlName.split(";")[1]; // we remove the date
                latestBlName = latestBlName.substring("baseline:".length()); // and
                                                                             // remove
                                                                             // the
                                                                             // "baseline:"
                                                                             // prefix
                return new Baseline(latestBlName);
            }
        }

        throw new ClearToolError("No baseline found on the component: " + comp.toString());
    }

    /** implements {@link CTFunctions#recommendAllEligibleBaselines(Stream)} **/
    @Override
    public void recommendAllEligibleBaselines(Stream stream) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("chstream");
        args.add("-recommended");
        args.add("-default");
        args.add(stream.toString());

        launcher.run(args, null);
    }

    
    public void rmview(View view) throws IOException, InterruptedException, ClearToolError {
        rmview(view, false);
    }
    
    /** implements {@link CTFunctions#rmview(View)} **/
    @Override
    public void rmview(View view, boolean useTag) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        FilePath execPath = null;
        
        args.add("rmview");
        args.add("-force");
        if (useTag || view.isDynamic()) {
            args.add("-tag", view.getName());
        } else {
            args.add(view.getName());
            execPath = getWorkspace();
        }
        launcher.run(args, execPath);
    }
    
    
    /** implements {@link CTFunctions#rmtag(View)} **/
    @Override
    public void rmtag(View view) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("rmtag");
        args.add("-view", view.getName());
        launcher.run(args, null);
    }
    
    /** implements {@link CTFunctions#unregister(View)} **/
    @Override
    public void unregister(View view) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("unregister");
        args.add("-view");
        args.add("-uuid", view.getUuid());
        launcher.run(args, null);
    }

    /** implements {@link CTFunctions#startView()} **/
    @Override
    public void startView(View view) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("startview");
        args.add(view.getName());
        launcher.run(args, null);
    }

    /** implements {@link CTFunctions#getComponents(Stream)} **/
    @Override
    public List<Component> getComponents(Stream stream) throws IOException, InterruptedException,
            ClearToolError
    {
        List<Component> components = getRWComponents(stream);
        components.addAll(getReadOnlyComponents(stream));
        return components;
    }

    /** implements {@link CTFunctions#getRWComponents(Stream)} **/
    @Override
    public List<Component> getRWComponents(Stream stream) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%[mod_comps]Xp");
        args.add(stream.toString());

        String cleartoolResult = launcher.run(args, null);

        List<Component> components = new ArrayList<Component>();
        if (cleartoolResult != null && cleartoolResult.contains("component:")) {
            Pattern pattern = Pattern.compile("component:([^\\s]+)");
            Matcher matcher = pattern.matcher(cleartoolResult);

            while (matcher.find()) {
                String comp = matcher.group(1).trim();
                if (!comp.equals("")) {
                    /* name@/pvob, read only */
                    components.add(new Component(comp, false));
                }
            }
        }

        return components;
    }

    /** implements {@link CTFunctions#getRWComponents(Stream)} **/
    @Override
    public List<Component> getReadOnlyComponents(Stream stream) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%[non_mod_comps]Xp");
        args.add(stream.toString());

        String cleartoolResult = launcher.run(args, null);

        List<Component> components = new ArrayList<Component>();
        if (cleartoolResult != null && cleartoolResult.contains("component:")) {
            Pattern pattern = Pattern.compile("component:([^\\s]+)");
            Matcher matcher = pattern.matcher(cleartoolResult);

            while (matcher.find()) {
                String comp = matcher.group(1).trim();
                if (!comp.equals("")) {
                    /* name@/pvob, read only */
                    components.add(new Component(comp, true));
                }
            }
        }

        return components;
    }

    /** implements {@link CTFunctions#getComponentRootPath(Component)} **/
    @Override
    public String getComponentRootPath(Component comp) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add("lscomp");
        args.add("-fmt", "%[root_dir]p");
        args.add(comp.toString());
        String result = this.launcher.run(args, null);

        while (result.startsWith("/") || result.startsWith("\\")) {
            result = result.substring(1);
        }

        return result.trim();
    }

    /** implements {@link CTFunctions#update(View)} **/
    @Override
    public void update(View view) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add("setcs");
        if (view.isDynamic()) {
            args.add("-tag", view.getName());
        } else {
            args.add("-force");
        }
        if (view.isUcm()) {
            args.add("-stream");
        } else {
            args.add("-current");
        }

        FilePath viewPath;
        if (view.getViewPath() != null) {
            /* if viewPath is already defined, we use it */
            viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
        } else {
            /* else, we use a child directory in the workspace/viewRoot */
            viewPath = getViewRootPath().child(view.getName());
        }
        launcher.run(args, viewPath);
    }

    /** implements {@link CTFunctions#getStreamFromView(View)} **/
    @Override
    public Stream getStreamFromView(View view) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%Xn");
        args.add("-view");
        args.add(view.getName());

        String result = launcher.run(args, null).trim();

        if (result.startsWith("stream:")) {
            return new Stream(result.trim().substring("stream:".length()));
        } else {
            throw new ClearToolError(String.format("No Stream attached to view %s", view.getName()));
        }
    }

    /** implements {@link CTFunctions#rebaseDynamicView(View, List)} **/
    @Override
    public void rebaseDynamicView(View view, List<Baseline> baselines) throws InterruptedException,
            IOException, ClearToolError
    {
        if (!view.isDynamic()) {
            throw new ClearToolError(view.getName() + " is not dynamic");
        }

        if (baselines.isEmpty()) {
            return;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("rebase");

        String blString = baselines.toString().replaceAll("[\\s\\[\\]]", "");

        args.add("-baseline", blString);
        args.add("-view", view.getName());
        args.add("-complete");

        launcher.run(args, null);
    }

    /**
     * implements
     * {@link CTFunctions#lshistory(HistoryFormatHandler, Date, View, String, List, String)}
     **/
    @Override
    public List<HistoryEntry> lshistory(HistoryFormatHandler formatHandler, Date sinceDate,
            View view, String branch, List<String> lookupPaths, String extendedViewPath)
            throws IOException, InterruptedException, ClearToolError, ParseException
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lshistory");
        args.add("-since", Tools.formatCleartoolDate(sinceDate));
        args.add("-fmt", formatHandler.getFormat() + COMMENT + LINEEND);
        if ((branch != null) && (branch.length() > 0)) {
            args.add("-branch", "brtype:" + branch);
        }
        args.add("-nco");
        /* 20111216: replaced '-all' by '-r', '-all' makes lshistory ignore the lookupPaths */
        args.add("-r"); 
        for (String path : lookupPaths) {
            args.add(path);
        }

        FilePath viewPath;
        if (view.getViewPath() != null) {
            /* if viewPath is already defined, we use it */
            viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
        } else {
            /* else, we use a child directory in the workspace/viewRoot */
            viewPath = getViewRootPath().child(view.getName());
        }
        
        String result;
        try {
            result = launcher.run(args, viewPath);
        } catch (ClearToolError e) {
            if (e.getResult().contains("cleartool: Error: Branch type not found:")
                    && e.getCode() == 0)
            {
                /*
                 * this can happen if we ask for a read only path in the view.
                 * we ignore this error
                 */
                result = e.getResult();
            } else {
                throw e;
            }
        }

        return parseLsHistory(result, formatHandler, extendedViewPath);
    }

    private List<HistoryEntry> parseLsHistory(String cleartoolOutput,
            HistoryFormatHandler formatHandler, String extendedViewPath) throws ParseException,
            IOException
    {
        List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        HistoryEntry currentEntry = null;

        StringBuilder commentBuilder = new StringBuilder();

        BufferedReader reader = new BufferedReader(new StringReader(cleartoolOutput));
        String line = reader.readLine();

        while (line != null) {

            if (line.startsWith("cleartool: Error: Branch type not found:")) {
                /*
                 * this can happen if we ask for a read only path in the view.
                 * we ignore this error
                 */
                line = reader.readLine();
                continue;
            }
            Matcher matcher = formatHandler.checkLine(line);

            // finder find start of lshistory entry
            if (matcher != null) {

                if (currentEntry != null) {
                    currentEntry.setComment(commentBuilder.toString());
                }

                commentBuilder = new StringBuilder();
                currentEntry = formatHandler.parseHistoryLine(matcher, line);

                String fileName = currentEntry.getElement();
                // Trim the extended view path
                if (extendedViewPath != null) {
                    if (fileName.startsWith(extendedViewPath)) {
                        fileName = fileName.substring(extendedViewPath.length());
                        currentEntry.setElement(fileName);
                    }
                }

                entries.add(currentEntry);

            } else {
                if (commentBuilder.length() > 0) {
                    commentBuilder.append("\n");
                }
                commentBuilder.append(line);
            }
            line = reader.readLine();
        }
        if (currentEntry != null) {
            currentEntry.setComment(commentBuilder.toString());
        }
        return entries;
    }

    /**
     * implements
     * {@link CTFunctions#lsactivity(String, HistoryFormatHandler, View)}
     **/
    @Override
    public UcmActivity lsactivity(String activityName, HistoryFormatHandler formatHandler, View view)
            throws IOException, InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsactivity");
        args.add("-fmt", formatHandler.getFormat());
        args.add(activityName);

        FilePath viewPath;
        if (view.getViewPath() != null) {
            /* if viewPath is already defined, we use it */
            viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
        } else {
            /* else, we use a child directory in the workspace/viewRoot */
            viewPath = getViewRootPath().child(view.getName());
        }
        
        String result;

        try {
            result = launcher.run(args, viewPath);
            ;
        } catch (ClearToolError e) {
            if (e.getResult().contains("not a deliver or rebase integration activity")) {
                /* error if we ask for %[contrib_acts]Xp on a normal activity */
                result = e.getResult().substring(e.getResult().indexOf('\n'));
                /* we get rid of the first error line */
            } else {
                throw e;
            }
        }

        return formatHandler.parseActivity(result);
    }

    /** implements {@link CTFunctions#catcs(View)} **/
    @Override
    public String catcs(View view) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("catcs");
        args.add("-tag", view.getName());

        return launcher.run(args, null);
    }

    /** implements {@link CTFunctions#setcs(View, String)} **/
    @Override
    public void setcs(View view, String configSpec) throws IOException, InterruptedException,
            ClearToolError
    {
        FilePath configSpecFile = launcher.getWorkspace().createTextTempFile(view.getName(),
                ".configSpec", configSpec);
        String csLocation = configSpecFile.getRemote();

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("setcs");
        args.add("-tag", view.getName());
        if (!view.isDynamic()) {
            args.add("-force");
        }
        args.add(csLocation);

        FilePath viewPath;
        if (view.getViewPath() != null) {
            /* if viewPath is already defined, we use it */
            viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
        } else {
            /* else, we use a child directory in the workspace/viewRoot */
            viewPath = getViewRootPath().child(view.getName());
        }
        
        launcher.run(args, viewPath);

        configSpecFile.delete();
    }

    /** implements {@link CTFunctions#getComponentFromBL(Baseline)} **/
    @Override
    public Component getComponentFromBL(Baseline baseline) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add("lsbl");
        args.add("-fmt", "%[component]Xp");
        args.add(baseline.toString());

        String result = launcher.run(args, null);

        if (result != null && result.trim().startsWith("component:")) {
            return new Component(result.trim().substring("component:".length()));
        } else {
            throw new ClearToolError(launcher.getExecutable() + ' ' + args.toStringWithQuote(),
                    "No component attached to baseline.", 1, launcher.getWorkspace());
        }
    }

    /** implements {@link CTFunctions#getLatestBaselines(Stream)} **/
    @Override
    public List<Baseline> getLatestBaselines(Stream stream) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%[latest_bls]Xp");
        args.add(stream.toString());

        String cleartoolResult = launcher.run(args, null);

        List<Baseline> baselines = new ArrayList<Baseline>();

        if (cleartoolResult != null && cleartoolResult.contains("baseline:")) {
            Pattern pattern = Pattern.compile("baseline:([^\\s]+)");
            Matcher matcher = pattern.matcher(cleartoolResult);
            while (matcher.find()) {
                String blFullName = matcher.group(1).trim();
                if (blFullName != null && !blFullName.equals("")) {
                    baselines.add(new Baseline(blFullName));
                }
            }
        }
        return baselines;
    }

    /**
     * implements
     * {@link CTFunctions#changeBaselinePromotionLevel(Baseline, String)}
     **/
    @Override
    public void changeBaselinePromotionLevel(Baseline baseline, PromotionLevel level)
            throws InterruptedException, IOException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("chbl");
        args.add("-c", "Hudson changed promotion level to " + print(level));
        args.add("-level", print(level));
        args.add(baseline.toString());

        launcher.run(args, null);
    }

    /** implements {@link CTFunctions#lsvob()} **/
    @Override
    public List<String> lsvob() throws IOException, InterruptedException, ClearToolError {

        ArgumentListBuilder args = new ArgumentListBuilder("lsvob", "-s");

        String result = launcher.run(args, null);

        List<String> vobs = new ArrayList<String>();
        if (result != null) {
            for (String vobName : result.trim().split("\\n")) {
                vobs.add(vobName);
            }
        }

        return vobs;
    }

    /** implements {@link CTFunctions#getAllStreamsFromVob(String)} **/
    @Override
    public List<Stream> getAllStreamsFromVob(String vobTag) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add("lsstream");
        args.add("-s");
        args.add("-invob", vobTag);

        String result = null;
        List<Stream> streams = new ArrayList<Stream>();

        try {
            result = launcher.run(args, null);
            for (String streamName : result.split("\\n")) {
                streams.add(new Stream(streamName, vobTag));
            }
        } catch (ClearToolError e) {
            if (e.getResult().contains("Not a UCM PVOB")) {
                /* no streams attached to a normal vob, we do nothing :-) */
            } else {
                throw e;
            }
        }

        return streams;
    }

    /** implements {@link CTFunctions#getPromotionLevels(String)} **/
    @Override
    public void fetchPromotionLevels(String vobTag) throws IOException, InterruptedException {
        // vob dependant REJECTED Promotion level
        try {
            this.promotionLevelNames.put(PromotionLevel.REJECTED, getAttributeFromVob(
                    REJECTED_PLEVEL_ATTR, vobTag));
        } catch (ClearToolError err) {
            this.promotionLevelNames.put(PromotionLevel.REJECTED, PromotionLevel.DEFAULT_REJECTED);
        }
        // vob dependant BUILT Promotion level
        try {
            this.promotionLevelNames.put(PromotionLevel.BUILT, getAttributeFromVob(
                    BUILT_PLEVEL_ATTR, vobTag));
        } catch (ClearToolError err) {
            this.promotionLevelNames.put(PromotionLevel.BUILT, PromotionLevel.DEFAULT_BUILT);
        }
        // vob dependant RELEASED Promotion level
        try {
            this.promotionLevelNames.put(PromotionLevel.RELEASED, getAttributeFromVob(
                    RELEASED_PLEVEL_ATTR, vobTag));
        } catch (ClearToolError err) {
            this.promotionLevelNames.put(PromotionLevel.RELEASED, PromotionLevel.DEFAULT_RELEASED);
        }
    }

    public String getAttributeFromVob(String attrName, String vobTag) throws IOException,
            InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("describe");
        args.add("-fmt", "%[" + attrName + "]NSa");
        args.add("vob:" + vobTag);
        String result = launcher.run(args, null).trim();

        if (result != null && !result.isEmpty()) {
            Matcher m = Pattern.compile("\"(.*)\"").matcher(result);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new ClearToolError("Attribute: " + attrName + " not found on vob: " + vobTag);
    }

    /** implements {@link CTFunctions#lockStream(Stream)} **/
    @Override
    public void lockStream(Stream stream) throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lock");
        args.add("-c", "Hudson locked the stream");
        args.add("stream:" + stream.toString());

        launcher.run(args, null);
    }

    /** implements {@link CTFunctions#unlockStream(Stream)} **/
    @Override
    public void unlockStream(Stream stream) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("unlock");
        args.add("-c", "Hudson unlocked the stream");
        args.add("stream:" + stream.toString());

        launcher.run(args, null);
    }

    /** implements {@link CTFunctions#getViewInfo(View)} **/
    @Override
    public boolean getViewInfo(View view) throws IOException, InterruptedException, ClearToolError {
        try {
            View myView = getViewInfo(view.getName());
            view.setUuid(myView.getUuid());
            view.setGlobalPath(myView.getGlobalPath());
            view.setLocalPath(myView.getLocalPath());
            view.setDynamic(myView.isDynamic());
            view.setUcm(myView.isUcm());
        } catch (ClearToolError e) {
            if (e.getResult().contains("No matching entries found for view tag")) {
                /* the view tag is not registered on the server */
                return false;
            } else {
                throw e;
            }
        }
        return true;
    }

    /** implements {@link CTFunctions#getViewInfo(String)} **/
    @Override
    public View getViewInfo(String viewTag) throws IOException, InterruptedException,
            ClearToolError
    {
        if (viewTag == null || viewTag.isEmpty()) {
            return null;
        }
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsview");
        args.add("-l");
        args.add("-prop");
        args.add(viewTag);

        String result = launcher.run(args, null);
        View view = null;

        if (result != null) {
            Matcher infoMatch = VIEW_INFO_PATTERN.matcher(result);
            if (infoMatch.find()) {
                view = new View(viewTag);
                view.setGlobalPath(infoMatch.group(1).trim());
                view.setLocalPath(infoMatch.group(2).trim());
                view.setUuid(infoMatch.group(3).trim());
                Matcher attrMatch = VIEW_ATTRIBUTES_PATTERN.matcher(result);
                if (attrMatch.find()) {
                    String attributes = attrMatch.group(1).trim();
                    view.setDynamic(!attributes.contains("snapshot"));
                    view.setUcm(attributes.contains("ucmview"));
                } else {
                    view.setDynamic(true);
                    view.setUcm(false);
                }
            } else {
                throw new ClearToolError("lsview parsing error", launcher.getExecutable() + " "
                        + args.toStringWithQuote(), result, 0, getWorkspace());
            }
        }
        return view;
    }

    /** implements {@link CTFunctions#getViewsFromStream(Stream)} **/
    @Override
    public List<View> getViewsFromStream(Stream stream) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%[views]p");
        args.add(stream.toString());

        String result = null;
        try {
            result = launcher.run(args, null);
        } catch (ClearToolError e) {
            if (e.getResult().contains("No tag in region for view")) {
                /*
                 * This should not be an error from cleartool but only a warning
                 * This happens when a view tag has been deleted but that the
                 * view is still registered and attached to the stream so we
                 * ignore the error :-)
                 * 
                 * In order to solve this problem, you can launch this command:
                 * $ cleartool rmview �force -avobs �uuid <view-uuid>
                 */
                result = e.getResult();
            } else {
                throw e;
            }
        }

        List<View> views = new ArrayList<View>();
        if (result != null) {
            for (String line : result.split("\n")) {
                if (!line.contains("cleartool: Error:")) {
                    /*
                     * skipping the error lines that we may have got in the
                     * errors we escaped just before
                     */
                    String[] viewTags = line.trim().split(" ");
                    for (String viewTag : viewTags) {
                        views.add(new View(viewTag, stream));
                    }
                }
            }
        }

        return views;
    }

    /**
     * implements {@link CTFunctions#getSnapshotViewUuid(FilePath)}
     * 
     * @throws IOException
     **/
    @Override
    public String getSnapshotViewUuid(FilePath viewPath) throws ClearToolError, IOException {
        Pattern viewUuidPattern = Pattern.compile("view_uuid:([^\\s]+)");
        String viewDatFile = viewDatFile(viewPath);
        Matcher matcher;
        try {
            matcher = viewUuidPattern.matcher(viewPath.child(viewDatFile).readToString());
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new ClearToolError(viewDatFile + " file did not match the expected pattern");
            }
        } catch (IOException e) {
            throw new IOException("Could not open " + viewDatFile + " file.", e);
        }
    }

    /** implements {@link CTFunctions#lsStgloc()} **/
    @Override
    public List<String> lsStgloc() throws IOException, InterruptedException, ClearToolError {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstgloc");
        args.add("-s");

        String result = launcher.run(args, null);

        List<String> stgLocations = new ArrayList<String>();
        if (result != null && !result.isEmpty()) {
            String[] tab = result.split("[\\r\\n]+");
            for (String s : tab) {
                if (!s.trim().isEmpty())
                    stgLocations.add(s.trim());
            }
        }

        return stgLocations;
    }

    /** implements {@link CTFunctions#getStreamLockState(Stream)} **/
    @Override
    public LockState getStreamLockState(Stream stream) throws IOException, InterruptedException,
            ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lsstream");
        args.add("-fmt", "%[locked]p");
        args.add(stream.toString());

        String result = launcher.run(args, null);

        return LockState.value(result);
    }

    /** implements {@link CTFunctions#hasCheckouts(String, View)} **/
    @Override
    public boolean hasCheckouts(String branchname, View view, List<String> viewPaths) 
            throws IOException, InterruptedException, ClearToolError
    {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("lscheckout");
        args.add("-s");
        args.add("-brtype", branchname);
        
        if (view.isDynamic()) {
            args.add("-avobs");
        } else if (viewPaths != null && viewPaths.size() > 0) {
            args.add("-r");
            for (String pname : viewPaths) {
                args.add(pname);
            }
        } else {
            throw new ClearToolError("Cannot search for checkouts in a snapshot view without load rules.");
        }

        FilePath viewPath;
        if (view.getViewPath() != null) {
            /* if viewPath is already defined, we use it */
            viewPath = new FilePath(getViewRootPath().getChannel(), view.getViewPath());
        } else {
            /* else, we use a child directory in the workspace/viewRoot */
            viewPath = getViewRootPath().child(view.getName());
        }
        
        String result = launcher.run(args, viewPath);

        return result.trim().length() > 0;
    }

    /**
     * Adaptation to make it work on unix
     * 
     * @param viewPath
     * @return
     */
    private String viewDatFile(FilePath path) {
        if (Tools.isWindows(path)) {
            return "view.dat";
        } else {
            return ".view.dat";
        }
    }

    
    @Override
    public boolean deliver(Stream sourceStream, Stream targetStream, View targetView,
            Baseline baseline, boolean cancelIfNonTrivial) throws IOException,
            InterruptedException, ClearToolError
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void deliverComplete(Stream sourceStream, Stream targetStream, View targetView,
            Baseline baseline) throws IOException, InterruptedException, ClearToolError
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void deliverCancel(Stream sourceStream, Stream targetStream, View targetView)
            throws IOException, InterruptedException, ClearToolError
    {
        // TODO Auto-generated method stub

    }

    /*******************************
     **** GETTERS & SETTERS ********
     *******************************/

    public String print(PromotionLevel level) {
        return this.promotionLevelNames.get(level);
    }

    public FilePath getWorkspace() {
        return this.launcher.getWorkspace();
    }

    public CTLauncher getLauncher() {
        return this.launcher;
    }

    public EnvVars getEnv() {
        return this.launcher.getEnv();
    }

    public File getLogFile() {
        return this.launcher.getLogFile();
    }

}
