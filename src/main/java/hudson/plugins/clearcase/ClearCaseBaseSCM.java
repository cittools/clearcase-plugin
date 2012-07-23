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

import hudson.Extension;
import hudson.model.Computer;
import hudson.plugins.clearcase.changelog.BaseChangeLogParser;
import hudson.plugins.clearcase.checkout.CheckoutAction;
import hudson.plugins.clearcase.checkout.DynamicCheckoutAction;
import hudson.plugins.clearcase.checkout.SnapshotCheckoutAction;
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.history.BaseHistoryAction;
import hudson.plugins.clearcase.history.HistoryAction;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Base ClearCase SCM.
 * 
 * This SCM is for base ClearCase repositories.
 * 
 * @author Erik Ramfelt
 */
public class ClearCaseBaseSCM extends AbstractClearCaseSCM {

    /*******************************
     **** CONSTANTS ****************
     *******************************/
    
    @Extension
    public static final ClearCaseBaseSCMDescriptor 
                                   BASE_DESCRIPTOR = new ClearCaseBaseSCMDescriptor();
    
    /*******************************
     **** FIELDS *******************
     *******************************/
    
    private final String configSpec;
    private final String branch;
    
    
    /*******************************
     **** CONSTRUCTOR **************
     *******************************/

    @DataBoundConstructor
    public ClearCaseBaseSCM(String viewName, 
                            String mkviewOptionalParam,
                            boolean filteringOutDestroySubBranchEvent, 
                            boolean useUpdate, 
                            String excludedRegions,
                            String loadRules, 
                            boolean useDynamicView, 
                            String viewDrive, 
                            int multiSitePollBuffer,
                            String clearcaseConfig,
                            boolean doNotUpdateConfigSpec,
                            String customWorkspace,
    
                            String configSpec,
                            String branch) {
        super(viewName, 
              mkviewOptionalParam, 
              filteringOutDestroySubBranchEvent, 
              useUpdate, 
              excludedRegions,
              loadRules, 
              useDynamicView, 
              viewDrive, 
              multiSitePollBuffer,
              clearcaseConfig,
              doNotUpdateConfigSpec,
              customWorkspace);
        this.branch = branch;
        this.configSpec = configSpec;
    }

    /*******************************
     **** OVERRIDE *****************
     *******************************/

    /** override method {@link hudson.scm.SCM#getDescriptor()} */
    @Override
    public SCMDescriptor<?> getDescriptor() {
        return ClearCaseBaseSCM.BASE_DESCRIPTOR;
    }
    
    /** implementation of abstract method {@link hudson.scm.SCM#createChangeLogParser()} */
    @Override
    public ChangeLogParser createChangeLogParser() {
        return new BaseChangeLogParser();
    }
    
    /** implementation of abstract method {@link AbstractClearCaseSCM#createView()} */
    @Override
    protected View createView(String viewTag) {
        return new View(viewTag, this.isUseDynamicView());
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#createCheckOutAction()} */
    @Override
    protected CheckoutAction createCheckoutAction(ClearTool ct, ClearCaseLogger logger, View view,
            String stgloc)
    {
        CheckoutAction action;
        if (isUseDynamicView()) {
            action = new DynamicCheckoutAction(ct, logger, view, stgloc, getMkviewOptionalParam(),
                    isUseUpdate(), getConfigSpec(), isDoNotUpdateConfigSpec(),
                    BASE_DESCRIPTOR.getTimeShift());
        } else {
            action = new SnapshotCheckoutAction(ct, logger, view, stgloc, getMkviewOptionalParam(),
                    isUseUpdate(), getConfigSpec(), getViewPaths(ct.getWorkspace()));
        }
        return action;
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#createHistoryAction()} */
    @Override
    protected HistoryAction createHistoryAction(ClearTool ct) {
        String nodeName = Computer.currentComputer().getName();
        
        BaseHistoryAction action = new BaseHistoryAction(ct, configureFilters(ct),
                fetchClearCaseConfig(nodeName).getChangeLogMergeTimeWindow());

        action.setExtendedViewPath(getExtendedViewPath(ct.getWorkspace()));

        return action;
    }

    /** implementation of abstract method {@link AbstractClearCaseSCM#getViewPathsForLSHistory()} */
    @Override
    protected List<String> getViewPathsForLSHistory(ClearTool ct) throws IOException,
            InterruptedException, ClearToolError {
        return this.getViewPaths(ct.getWorkspace());
    }


    /** implementation of abstract method {@link AbstractClearCaseSCM#getBranchNames()} */
    @Override
    public List<String> getBranchNames() {
        List<String> branchNames = new ArrayList<String>();
        if (this.branch != null) {
            // split by whitespace, except "\ "
            for(String br : this.branch.split("(?<!\\\\)[ \\r\\n]+")) {
                // now replace "\ " by " ".
                branchNames.add(br.replaceAll("\\\\ ", " "));
            }
        }
        return branchNames;
    }
    


    /*******************************
     **** GETTERS ******************
     *******************************/
    
    public String getBranch() {
        return this.branch;
    }

    public String getConfigSpec() {
        return this.configSpec;
    }



}
