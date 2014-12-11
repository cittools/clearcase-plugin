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
package hudson.plugins.clearcase;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.StringParameterValue;
import hudson.plugins.clearcase.changelog.UcmChangeLogParser;
import hudson.plugins.clearcase.checkout.CheckoutAction;
import hudson.plugins.clearcase.checkout.UcmDynamicCheckoutAction;
import hudson.plugins.clearcase.checkout.UcmSnapshotCheckoutAction;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.history.HistoryAction;
import hudson.plugins.clearcase.history.UcmHistoryAction;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.Component;
import hudson.plugins.clearcase.objects.Stream;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.CCParametersAction;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * SCM for ClearCaseUCM. This SCM will create a UCM view from a and apply a list of load rules to
 * it.
 */
public class ClearCaseUcmSCM extends AbstractClearCaseSCM {

    /*******************************
     **** CONSTANTS ****************
     *******************************/

    @Extension
    public static final ClearCaseUcmSCMDescriptor UCM_DESCRIPTOR = new ClearCaseUcmSCMDescriptor();

    /*******************************
     **** FIELDS *******************
     *******************************/
    public static final String CLEARCASE_STREAM_ENVSTR = "CLEARCASE_STREAM";

    private final String stream;

    /*******************************
     **** CONSTRUCTOR **************
     *******************************/

    @DataBoundConstructor
    public ClearCaseUcmSCM(String viewName, String mkviewOptionalParam,
            boolean filteringOutDestroySubBranchEvent, boolean useUpdate, String excludedRegions,
            String loadRules, boolean useDynamicView, String viewDrive, int multiSitePollBuffer,
            String clearcaseConfig, boolean doNotUpdateConfigSpec, String customWorkspace,
            String stream)
    {
        super(viewName, mkviewOptionalParam, filteringOutDestroySubBranchEvent, useUpdate,
                excludedRegions, loadRules, useDynamicView, viewDrive, multiSitePollBuffer,
                clearcaseConfig, doNotUpdateConfigSpec, customWorkspace);

        this.stream = shortenStreamName(stream);
    }

    /*******************************
     **** OVERRIDE *****************
     *******************************/

    /** overrides {@link hudson.scm.SCM#getDescriptor()} */
    @Override
    public SCMDescriptor<?> getDescriptor() {
        return ClearCaseUcmSCM.UCM_DESCRIPTOR;
    }
    

    /** implementation of abstract method {@link hudson.scm.SCM#createChangeLogParser()} */
    @Override
    public ChangeLogParser createChangeLogParser() {
        return new UcmChangeLogParser();
    }


    /** overrides {@link AbstractClearCaseSCM#publishEnvVars()} */
    @Override
    public void publishEnvVars(FilePath workspace, EnvVars env) {
        super.publishEnvVars(workspace, env);
        if (workspace != null && getResolvedStreamName() != null) {
            env.put(CLEARCASE_STREAM_ENVSTR, getResolvedStreamName());
        }
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#getBranchNames()} */
    @Override
    public List<String> getBranchNames() {
        List<String> branchNames = new ArrayList<String>();
        String branch;
        if (!stream.equals(getResolvedStreamName())) {
            branch = getResolvedStreamName();
        } else {
            branch = stream;
        }
        if (branch.contains("@")) {
            branchNames.add(branch.split("@")[0]);
        }
        return branchNames;
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#createHistoryAction()} */
    @Override
    protected HistoryAction createHistoryAction(ClearTool ct) {
        UcmHistoryAction action = new UcmHistoryAction(ct, this.configureFilters(ct));

        action.setExtendedViewPath(getExtendedViewPath(ct.getWorkspace()));

        return action;
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#createCheckOutAction()} */
    @Override
    protected CheckoutAction createCheckoutAction(ClearTool ct, ClearCaseLogger logger, View view,
            String stgloc, int ccCmdDelay)
    {
        CheckoutAction action;
        if (isUseDynamicView()) {
            action = new UcmDynamicCheckoutAction(ct, logger, view, stgloc,
                    getMkviewOptionalParam(), isUseUpdate(), isDoNotUpdateConfigSpec(),
                    ClearCaseBaseSCM.BASE_DESCRIPTOR.getTimeShift());
        } else {
            action = new UcmSnapshotCheckoutAction(ct, logger, view, stgloc,
                    getMkviewOptionalParam(), isUseUpdate(), getViewPaths(ct.getWorkspace()),ccCmdDelay);
        }
        return action;
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#createView()} */
    @Override
    protected View createView(String viewTag) {
        return new View(viewTag, new Stream(getResolvedStreamName()), isUseDynamicView());
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#getViewPathsForLSHistory()} */
    @Override
    protected List<String> getViewPathsForLSHistory(ClearTool ct) throws IOException,
            InterruptedException, ClearToolError
    {

        List<String> viewPaths = getViewPaths(ct.getWorkspace());
        List<String> viewPathsForLSHistory = new ArrayList<String>();
        List<String> rwComponentPaths = new ArrayList<String>();

        for (Component comp : ct.getRWComponents(new Stream(getResolvedStreamName()))) {
            rwComponentPaths.add(ct.getComponentRootPath(comp));
        }

        for (String path : viewPaths) {
            for (String componentPath : rwComponentPaths) {
                if (path.startsWith(componentPath)) {
                    viewPathsForLSHistory.add(path);
                    break;
                }
            }
        }

        return viewPathsForLSHistory;
    }

    /*******************************
     **** UTILS ********************
     *******************************/

    private String shortenStreamName(String longStream) {
        if (longStream.startsWith("stream:")) {
            return longStream.substring("stream:".length());
        }
        return longStream;
    }

    @Override
    protected void publishBuildVariables(AbstractBuild<?, ?> build) {
        super.publishBuildVariables(build);
        if (getResolvedStreamName() != null) {
            CCParametersAction.addBuildParameter(build, new StringParameterValue(
                    CLEARCASE_STREAM_ENVSTR, getResolvedStreamName()));
        }
    }

    public static View getBuildView(AbstractBuild<?, ?> build) throws ClearToolError {
        View view = null;
        StringParameterValue nameParam = CCParametersAction.getBuildParameter(build,
                CLEARCASE_VIEWNAME_ENVSTR);
        if (nameParam == null) {
            throw new ClearToolError("Could not find clearcase view name into build parameters.");
        } else {
            view = new View(nameParam.value);
        }

        StringParameterValue pathParam = CCParametersAction.getBuildParameter(build,
                CLEARCASE_VIEWPATH_ENVSTR);
        if (pathParam == null) {
            throw new ClearToolError("Could not find clearcase view path into build parameters.");
        } else {
            view.setViewPath(pathParam.value);
        }

        StringParameterValue streamParam = CCParametersAction.getBuildParameter(build,
                CLEARCASE_STREAM_ENVSTR);
        if (streamParam == null) {
            throw new ClearToolError("Could not find clearcase stream into build parameters.");
        } else {
            view.setStream(new Stream(streamParam.value));
        }

        StringParameterValue typeParam = CCParametersAction.getBuildParameter(build,
                CLEARCASE_VIEWTYPE_ENVSTR);
        if (typeParam == null) {
            throw new ClearToolError("Could not find clearcase view type into build parameters.");
        } else {
            view.setDynamic(DYNAMIC_VIEW.equals(typeParam.value));
        }
        return view;
    }
    
    /*******************************
     **** GETTERS ******************
     *******************************/

    public String getStream() {
        return stream;
    }

    public String getResolvedStreamName() {
        if (this.getEnv() != null) {
            return this.getEnv().expand(stream);
        } else {
            return null;
        }
    }

}
