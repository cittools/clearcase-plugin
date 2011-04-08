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
package hudson.plugins.clearcase.checkout;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;
import java.util.List;

/**
 * Check out action that will check out files into a UCM snapshot view. Checking
 * out the files will also update the load rules in the view.
 */
public class UcmSnapshotCheckoutAction extends CheckoutAction {

    private final List<String> loadRules;
       
    
    public UcmSnapshotCheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view,
            String stgloc, String mkViewOptionalParams, boolean useUpdate, 
            List<String> loadRules)
    {
        super(cleartool, logger, view, stgloc, mkViewOptionalParams, useUpdate);
        this.loadRules = loadRules;
    }

    
    

    @Override
    public boolean checkout(FilePath workspace, TaskListener listener) throws IOException,
            InterruptedException, ClearToolError
    {
        boolean viewRegistered, viewFolderExists, viewExists, createView;
        String oldViewUuid = "null";
        createView = false;
        
        if (view.getStream() == null) {
            throw new ClearToolError("The stream was not specified.");
        }
        
        logger.log("Fetching view info...");
        try {
            viewRegistered = cleartool.getViewInfo(view);
        } catch (ClearToolError cte) {
            /* the server hosting the view was not reachable 
             * we consider that the view is registered */ 
            viewRegistered = true;
        }
        viewFolderExists = workspace.child(view.getName()).exists();
        if (viewFolderExists) {
            try {
                oldViewUuid = cleartool.getSnapshotViewUuid(workspace.child(view.getName()));
            } catch (Exception e) {
                /* either the view folder in the workspace does not exists,
                 * either the view.dat file could not be found in the view folder 
                 * either the view.dat file is corrupted and does not match the expected pattern */
                logger.log(e.toString());
            }
        }
        viewExists = viewRegistered && oldViewUuid.equals(view.getUuid());
        
        if (viewExists) {
            boolean correctStream = false;
            try {
                Stream currentStream = cleartool.getStreamFromView(view);
                correctStream = currentStream.equals(view.getStream());
                if (!correctStream) {
                    logger.log("Stream configuration has changed.");
                }
            } catch (ClearToolError e) {
                logger.log("WARNING: The view " + view.getName() 
                                                            + " is not attached to any stream.");
            }
            if (useUpdate && correctStream) {
                
                logger.log("Searching for changes in load rules...");
                String currentConfigSpec = cleartool.catcs(view);
                List<String> configSpecLoadRules = extractLoadRules(currentConfigSpec);
                
                if (loadRulesChanged(this.loadRules, configSpecLoadRules)) {
                    logger.log("Load rules have changed. Updating view...");
                    String newConfigSpec = makeNewConfigSpec(currentConfigSpec, this.loadRules);
                    cleartool.setcs(view, newConfigSpec);
                } else {
                    logger.log("No changes in load rules. Updating view...");
                    cleartool.update(view);
                }
            } else {
                logger.log("Deleting old view...");
                cleartool.rmview(view);
                createView = true;
            }
        } else {
            if (viewRegistered) {
                throw new ClearToolError(String.format("The view tag %s is already registered. " +
                        "Choose another one.", view.getName()));
            }
            if (viewFolderExists) {
                workspace.child(view.getName()).deleteRecursive();
                logger.log(String.format("There was a folder with a name " +
                		"conflicting with the view name %s, it was deleted.", view.getName()));
            }
            createView = true;
        }
        if (createView) {
            logger.log(String.format("Creating view: %s...", view.getName()));
            cleartool.mkview(view, stgloc, cleartool.getEnv().expand(mkViewOptionalParams));
            String newConfigSpec = cleartool.catcs(view);
            String configSpec = makeNewConfigSpec(newConfigSpec, this.loadRules);
            logger.log(String.format("Loading files from load rules..."));
            cleartool.setcs(view, configSpec);
        }
        
        return true;
    }

}
