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
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.plugins.clearcase.util.Tools;

import java.io.IOException;
import java.util.List;

public class UcmSnapshotCheckoutAction extends CheckoutAction {

	private final List<String> loadRules;

	public UcmSnapshotCheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view,
			String stgloc, String mkViewOptionalParams, boolean useUpdate, List<String> loadRules, int ccCmdDelay)
	{
		super(cleartool, logger, view, stgloc, mkViewOptionalParams, useUpdate, ccCmdDelay);
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

		if (view.getStream() == null) {
			throw new ClearToolError("The stream was not specified.");
		}

		View existingView = new View(view.getName());
		logger.log("Fetching view info...");
		try {
			viewRegistered = cleartool.getViewInfo(existingView);
		} catch (ClearToolError cte) {
			/*
			 * the server hosting the view was not reachable we consider that the view is registered
			 */
			viewRegistered = true;
		}
		viewFolderExists = workspace.child(existingView.getName()).exists();
		if (viewFolderExists) {
			try {
				oldViewUuid = cleartool
				.getSnapshotViewUuid(workspace.child(existingView.getName()));
			} catch (Exception e) {
				/*
				 * either the view folder in the workspace does not exists, either the view.dat file
				 * could not be found in the view folder either the view.dat file is corrupted and
				 * does not match the expected pattern
				 */
				logger.log(e.toString());
			}
		}
		viewExists = viewRegistered && oldViewUuid.equals(existingView.getUuid());

		if (viewExists) {
			boolean correctStream = false;
			try {
				Stream currentStream = cleartool.getStreamFromView(existingView);
				correctStream = currentStream.equals(view.getStream());
				if (!correctStream) {
					logger.log("Stream configuration has changed.");
				}
			} catch (ClearToolError e) {
				logger.log("WARNING: The view " + view.getName()
						+ " is not attached to any stream.");
			}
			if (useUpdate && correctStream) {
				//ending viewserver process to prevent any update action in progress.
				//useful if a previous update has been killed    
				logger.log("Ending vue and Sleeping for "+ccCmdDelay + " seconds ... ");
				cleartool.endviewServer(existingView,ccCmdDelay);
				try{ 

					logger.log("Searching for changes in load rules...");
					ConfigSpec configSpec = new ConfigSpec(cleartool.catcs(existingView));            
					if (configSpec.loadRulesDiffer(this.loadRules)) {
						logger.log("Load rules have changed. Updating view...");
						configSpec.replaceLoadRules(this.loadRules, Tools.isWindows(workspace));
						cleartool.setcs(existingView, configSpec.getValue());
					} else {
						logger.log("No changes in load rules. Updating view...");
						cleartool.update(existingView);
					}
				}finally{
					cleartool.endviewServer(existingView,ccCmdDelay);
				}
			}
			//prod00136760 : throw an error instead of deleting the view if the stream has changed and if the "reuse view" option is checked
			else if(useUpdate && !correctStream){
				throw new ClearToolError("The stream has changed but the \"reuse view\" option is checked so the plugin can't delete this view. " +
						"Please uncheck this option to delete and recreate the view.");
			}
			else {
				logger.log("Deleting view and Sleeping for "+ccCmdDelay + " seconds ...");
				cleartool.rmview(existingView,false, ccCmdDelay);
				createView = true;
			}
		} else {
			if (viewRegistered) {
				throw new ClearToolError(String.format("The view tag %s is already registered. "
						+ "Choose another one.", view.getName()));
			}
			if (viewFolderExists) {
				workspace.child(view.getName()).deleteRecursive();
				logger.log(String.format("There was a folder with a name "
						+ "conflicting with the view name %s, it was deleted.", view.getName()));
			}
			createView = true;
		}
		if (createView) {
			logger.log(String.format("Creating view: %s...", view.getName()));
			cleartool.mkview(view, stgloc, cleartool.getEnv().expand(mkViewOptionalParams));
			ConfigSpec configSpec = new ConfigSpec(cleartool.catcs(view));
			configSpec.replaceLoadRules(this.loadRules, Tools.isWindows(workspace));
			logger.log(String.format("Loading files from load rules..."));
			cleartool.setcs(view, configSpec.getValue());
		}

		return true;
	}

}
