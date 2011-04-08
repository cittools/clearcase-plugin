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

import hudson.FilePath;
import hudson.plugins.clearcase.objects.Baseline;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.CompositeComponent;
import hudson.plugins.clearcase.objects.HistoryEntry;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.UcmActivity;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.objects.Baseline.PromotionLevel;
import hudson.plugins.clearcase.objects.Stream.LockState;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

public interface CTFunctions {

    /**
     * Creates and registers a view
     * 
     * @param view
     *            the name of the view
     * @param stgLoc
     *            the name or path to a storage location where to store the view's files
     *            if the parameter is null or is an empty string, the plugin will use the default
     *            storage location of the computer where the command is launched.
     * @param optionalMkviewParameters
     *            optional parameters to pass to the mkview command
     */
    void 
    mkview(View view, String stgLoc, String optionalMkviewParameters) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Removes the view from a VOB
     * 
     * @param view
     */
    void 
    rmview(View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Starts or connects to a dynamic view's view_server process
     * 
     * @param view
     */
    void 
    startView(View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Retrives the config spec for the specified viewname
     * 
     * @param viewName
     *            the name of the view
     * @return a string containing the config spec
     */
    String 
    catcs(View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Sets the config spec of the view
     * 
     * The view tag does need not be active.
     * However, it is possible to set the config spec of a dynamic view from anywhere
     * using "-tag viewTag" 
     * @see http://www.ipnom.com/ClearCase-Commands/setcs.html
     * 
     * To set the config spec of a snapshot view, you must be in or under the
     * snapshot view root directory.
     * 
     * @param view
     *            the name of the view
     * @param configSpec
     *            the name of the file containing a config spec
     */
    void 
    setcs(View view, String configSpec) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Update the view with its latest configuration
     * 
     *<ul>
     *<li>For UCM views, this method verifies that the view's configuration
     * matches the configuration defined by the stream it is attached to and, if
     * needed, reconfigures the view. Load rules already in the view's
     * configuration are preserved.</li>
     * 
     *<li>In a snapshot view, this method initiates an update -noverwrite
     * operation for the current view and generates an update logfile with the
     * default name and location. (For information about this log file, see the
     * update reference page.)</li>
     * 
     * <li>Causes the view_server to flush its caches and reevaluate the current
     * config spec, which is stored in file config_spec in the view storage
     * directory. This includes:
     * 
     * <ul>
     * <li>Evaluating time rules with nonabsolute specifications (for example,
     * now, Tuesday)</li>
     * <li>Reevaluating –config rules, possibly selecting different derived
     * objects than previously</li>
     * <li>Re-reading files named in include rules</li>
     * </ul>
     *</ul>
     * 
     * @param view
     *            the name of the view
     */
    void 
    update(View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Returns a list of lshistory entries
     * 
     * @param format
     *            format that should be used by the lshistory command
     * @param lastBuildDate
     *            lists events recorded since (that is, at or after) the
     *            specified date-time
     * @param view
     *            the view context
     * @param branch
     *            the name of the branch to get history events for; if null then
     *            history events for all branches are listed
     * @param pathsInView
     *            view paths that should be added to the lshistory command. The
     *            view paths must be relative.
     */
    List<HistoryEntry> 
    lshistory(HistoryFormatHandler format, Date lastBuildDate, View view, String branch, 
              List<String> lookupPaths, String extendedViewPath) 
    throws IOException, InterruptedException, ClearToolError, ParseException;

    /**
     * Gets info about an UCM activity
     * 
     * @throws InterruptedException
     * @throws IOException
     * @return reader containing command output
     */
    UcmActivity 
    lsactivity(String activityName, HistoryFormatHandler formatHandler, View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Lists VOB registry entries
     * 
     * @return list of vob names
     */
    List<String> 
    lsvob() throws 
    IOException, InterruptedException, ClearToolError;

    /**
     * Gets all the streams hosted in a vob.
     * 
     * NOTE: if the vob is not an UCM PVOB, the method returns an empty list
     * 
     * @param vobTag
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Stream> 
    getAllStreamsFromVob(String vobTag) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Sets the promotion levels to use when promoting/demoting baselines.
     * 
     * Those levels must be attributes on the project vob given as parameter of this method.
     * If the attributes are not set or cannot be retrieved, the plugin will use default values.
     * 
     * @param vobTag
     * @throws IOException
     * @throws InterruptedException
     */
    void
    fetchPromotionLevels(String vobTag) 
    throws IOException, InterruptedException;

    /**
     * Create baselines on a list of components
     * 
     * @param view
     *          this is the view attached to the stream where we want to create baselines
     * @param components
     *          the list of the components where to put baselines, if the list is empty or null
     *          the plugin will use the option "-all"
     * @param identical
     *          if true, the baselines will be created even if there was no changes in the 
     *          components
     * @param full
     *          if true, the baselines created will be full, else, they will be incremental
     * @param baseName
     *          the baseline name that will be used by Clearcase. If the baseline is created 
     *          on more than one component, Clearcase will add a random appendix 
     *          to that name for each component.
     * @param comment
     *          the baseline comment, ignored if null
     *          
     * @return a list of the created baselines
     * 
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Baseline> 
    makeBaselines(View view, List<Component> components, boolean identical, boolean full, 
                  String baseName, String comment)
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Returns a list of the current baselines attached to the stream. 
     * 
     * @param stream
     * @return a list of all the latest baselines existing on the stream.
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Baseline> 
    getLatestBaselines(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Changes the promotion level of a baseline
     * 
     * @param baseline
     * @param level
     * @throws InterruptedException
     * @throws IOException
     * @throws ClearToolError
     */
    void 
    changeBaselinePromotionLevel(Baseline baseline, PromotionLevel level) 
    throws InterruptedException, IOException, ClearToolError;

    /**
     * Recommends all baselines that meet the promotion level specification; 
     * they are the latest baselines created in the stream at or above the specified 
     * promotion level for the project containing ths stream, or its foundation baselines 
     * if none has been created in the stream.
     * 
     * @param stream
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    void 
    recommendAllEligibleBaselines(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Rebase a dynamic view to the list of baselines specified
     * 
     * @param dynamicView
     * @param baselines
     * @throws InterruptedException
     * @throws IOException
     * @throws ClearToolError
     */
    void 
    rebaseDynamicView(View dynamicView, List<Baseline> baselines) 
    throws InterruptedException, IOException, ClearToolError;

    /**
     * Retrieves the component on which a baseline is put.
     * 
     * @param baseline
     * @param launcher
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    Component 
    getComponentFromBL(Baseline baseline) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * retrieves the stream from a view tag
     * 
     * @param viewName
     * @return
     * @throws ClearToolError
     * @throws InterruptedException
     * @throws IOException
     */
    Stream 
    getStreamFromView(View view) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Get all components from a stream
     * 
     * @param stream
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Component> 
    getComponents(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Get read/write components from a stream
     * 
     * @param stream
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Component> 
    getRWComponents(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Get read only components from a stream
     * 
     * @param stream
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Component> 
    getReadOnlyComponents(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;
    
    
    /**
     * Get the root path of a component
     * 
     * @param comp
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    String 
    getComponentRootPath(Component comp) 
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Locks the stream used during build to ensure the streams integrity during
     * the whole build process, i.e. we want to make sure that no delivers are
     * made to the stream during the build.
     * 
     * @param stream
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    void 
    lockStream(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Unlocks the stream used during build.
     * 
     * @param stream
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    void 
    unlockStream(Stream stream) 
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Retrieve information about a view.
     * 
     * @param viewTag
     *          the view tag must be not null and not empty
     * @return a {@link View} object, null if the tag is not registered on the server
     *          
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    View
    getViewInfo(String viewTag)
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Retrieve information about a view.
     * 
     * @param view
     *          the view must be not null and its tag must be initialized
     * @return true if all is ok, false if the tag is not registered on the server
     *          
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    boolean
    getViewInfo(View view)
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Retrieve all views from a stream
     * 
     * @param stream
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<View>
    getViewsFromStream(Stream stream)
    throws IOException, InterruptedException, ClearToolError;
    
    /**
     * Retrieve all baselines that depend on one.
     * @param baseline
     *          if this baseline is not composite, the method will return an empty list.
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Baseline>
    getDependingBaselines(Baseline baseline)
    throws IOException, InterruptedException, ClearToolError;
    
    
    /**
     * Retrieve a snapshot view uuid from the view.dat file stored at the root of the view.
     *  
     * @param viewPath
     * @return
     * @throws IOException
     * @throws ClearToolError 
     */
    String
    getSnapshotViewUuid(FilePath viewPath)
    throws IOException, ClearToolError;
    
    /**
     * Retrieve the list of storage locations registered on the clearcase server.
     * 
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<String>
    lsStgloc()
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Retrieve the lock state of a stream.
     * 
     * @param stream
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    LockState
    getStreamLockState(Stream stream)
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Get the latest baseline created on a component.
     * 
     * @param comp
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    Baseline 
    getLatestBl(Component comp, Stream stream)
    throws IOException, InterruptedException, ClearToolError;

    /**
     * Create a composite baseline on a composite component.
     * The baseline will be created regardless there were changes on the depending components.
     * If there were changes in the depending components, baselines will also be created on those
     * components before creating the composite baseline.
     * 
     * @param view
     * @param cComp
     * @param fullBaseline
     * @param baseName
     * @param comment
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    List<Baseline> 
    makeCompositeBaseline(View view, CompositeComponent cComp, boolean identical, 
                          boolean fullBaseline, String baseName, String comment) 
    throws IOException, InterruptedException, ClearToolError;
    
    
    /**
     * Returns true if it finds checkouts in a branch
     * 
     * @param branch name
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ClearToolError
     */
    boolean
    hasCheckouts(String branch, View view)
    throws IOException, InterruptedException, ClearToolError;
    
    
}

