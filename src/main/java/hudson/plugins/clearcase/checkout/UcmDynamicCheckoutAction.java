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

import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.StringParameterValue;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.ConfigSpec;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.CCParametersAction;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class UcmDynamicCheckoutAction extends CheckoutAction {

    private final boolean doNotUpdateConfigSpec;
    private final int timeShift;
    private final boolean freezeView;

    public UcmDynamicCheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view,
            String stgloc, String mkViewOptionalParams, boolean useUpdate,
            boolean doNotUpdateConfigSpec, int timeShift)
    {
        this(cleartool, logger, view, stgloc, mkViewOptionalParams, useUpdate,
                doNotUpdateConfigSpec, timeShift, true);
    }

    public UcmDynamicCheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view,
            String stgloc, String mkViewOptionalParams, boolean useUpdate,
            boolean doNotUpdateConfigSpec, int timeShift, boolean freezeView)
    {
        super(cleartool, logger, view, stgloc, mkViewOptionalParams, useUpdate,0);
        this.doNotUpdateConfigSpec = doNotUpdateConfigSpec;
        this.timeShift = timeShift;
        this.freezeView = freezeView;
    }

    @Override
    public boolean checkout(@SuppressWarnings("rawtypes") AbstractBuild build, TaskListener listener)
            throws IOException, InterruptedException, ClearToolError
    {
        boolean viewExists = false, createView = false;

        if (view.getStream() == null) {
            throw new ClearToolError("The stream was not specified.");
        }

        View existingView = new View(view.getName());
        logger.log("Fetching view info...");
        try {
            viewExists = cleartool.getViewInfo(existingView);
        } catch (ClearToolError cte) {
            /*
             * the server hosting the view was not reachable we consider that the view already
             * exists therefore, this view tag cannot be used
             */
            throw new ClearToolError("The view tag : " + view + " is already in use"
                    + " but the view's host cannot be reached," + " please use another view tag.",
                    cte);
        }

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
            if (!(useUpdate && correctStream)) {
                logger.log("Deleting view " + existingView + "...");
                cleartool.rmview(existingView);
                createView = true;
            }
        } else {
            createView = true;
        }

        if (createView) {
            logger.log("Creating dynamic view: " + view + "...");
            cleartool.mkview(view, stgloc, cleartool.getEnv().expand(mkViewOptionalParams));
        }

        logger.log("Starting view: " + view + "...");
        cleartool.startView(view);

        if (!doNotUpdateConfigSpec) {
            logger.log("Synchronizing view with stream...");
            cleartool.update(view);

            ConfigSpec configSpec = new ConfigSpec(cleartool.catcs(view));
            /* we store the config spec in order to restore it at the end of the build */
            CCParametersAction.addBuildParameter(build, new StringParameterValue(
                    ORIGINAL_CONFIG_SPEC, configSpec.getValue()));

            if (freezeView) {
                /*
                 * We add "-time" rules next to the element with "LATEST" rules. This way, we are
                 * sure that the view contents will not change during the build.
                 */
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, timeShift);
                Date time = cal.getTime();
                logger.log("Freezing view at " + time + "...");
                configSpec.addTimeRules(time);
            }
            cleartool.setcs(view, configSpec.getValue());
        }

        return true;
    }

}
