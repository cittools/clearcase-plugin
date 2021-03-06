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
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.ConfigSpec;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.Tools;

import java.io.IOException;
import java.util.List;


public class SnapshotCheckoutAction extends CheckoutAction {

	// optional fields for snapshot views
	private final String configSpec;
	private final List<String> loadRules;


	public SnapshotCheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view,
			String stgloc, String mkViewOptionalParams, boolean useUpdate, 
			String configSpec, List<String> loadRules, int ccCmdDelay)
	{
		super(cleartool, logger, view, stgloc, mkViewOptionalParams, useUpdate,ccCmdDelay);
		this.configSpec = configSpec;
		this.loadRules = loadRules;
	}


	@Override
	public boolean checkout(@SuppressWarnings("rawtypes") AbstractBuild build, TaskListener listener)
	throws IOException, InterruptedException, ClearToolError 
	{
		boolean viewRegistered, viewFolderExists, viewExists, createView;
		FilePath workspace = build.getWorkspace();
		String oldViewUuid = "null";
		createView = false;

		View existingView = new View(view.getName());
		logger.log("Fetching view info...");
		try {
			viewRegistered = cleartool.getViewInfo(existingView);
		} catch (ClearToolError cte) {
			/* the server hosting the view was not reachable 
			 * we consider that the view is registered */ 
			viewRegistered = true;
		}
		viewFolderExists = workspace.child(existingView.getName()).exists();
		if (viewFolderExists) {
			try {
				oldViewUuid = cleartool.getSnapshotViewUuid(workspace.child(existingView.getName()));
			} catch (Exception e) {
				/* either the view folder in the workspace does not exists,
				 * either the view.dat file could not be found in the view folder 
				 * either the view.dat file is corrupted and does not match the expected pattern */
				logger.log(e.toString());
			}
		}
		viewExists = viewRegistered && oldViewUuid.equals(existingView.getUuid());


		ConfigSpec jobConfSpec = new ConfigSpec(cleartool.getEnv().expand(configSpec));
		jobConfSpec.replaceLoadRules(loadRules, Tools.isWindows(workspace));

		if (viewExists) {
			if (useUpdate) {
				//ending viewserver process to prevent any update action in progress.
				//useful if a previous update has been killed
				logger.log("Ending vue and Sleeping for "+ccCmdDelay + " seconds ...");
				cleartool.endviewServer(existingView,ccCmdDelay);

				try{   
					logger.log("Searching for changes in config spec...");
					ConfigSpec viewConfigSpec = new ConfigSpec(cleartool.catcs(existingView).trim());
					if (jobConfSpec.equals(viewConfigSpec)){
						logger.log("No changes in config spec. Updating view...");
						cleartool.update(existingView);
					} else {
						logger.log("Config spec has changed. Updating view...");
						cleartool.setcs(existingView, jobConfSpec.getValue());
					}
				}finally{
					cleartool.endviewServer(existingView,ccCmdDelay);
				}
			} else {
				logger.log("Deleting view and Sleeping for "+ccCmdDelay + " seconds ...");
				cleartool.rmview(existingView,false,ccCmdDelay);                
				createView = true;
			}
		} else {
			if (viewRegistered){
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
			logger.log("Creating view: " + view + "...");
			cleartool.mkview(view, stgloc, cleartool.getEnv().expand(mkViewOptionalParams));
			logger.log("Setting config spec & updating view...");
			cleartool.setcs(view, jobConfSpec.getValue());
		}

		return true;
	}

}
