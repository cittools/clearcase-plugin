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
import hudson.plugins.clearcase.cleartool.ClearTool;
import hudson.plugins.clearcase.log.ClearCaseLogger;
import hudson.plugins.clearcase.objects.View;
import hudson.plugins.clearcase.util.ClearToolError;

import java.io.IOException;

public abstract class CheckoutAction {
    
    public static final String ORIGINAL_CONFIG_SPEC = "ORIGINAL_CONFIG_SPEC";
    
    /************
     ** FIELDS **
     ************/
    protected final ClearTool cleartool;
    protected final ClearCaseLogger logger;
    protected final View view;
    protected final String stgloc;
    protected final String mkViewOptionalParams;
    protected final boolean useUpdate;
    protected final int ccCmdDelay;
    
    /*****************
     ** CONSTRUCTOR **
     *****************/
	public CheckoutAction(ClearTool cleartool, ClearCaseLogger logger, View view, String stgloc,
			String mkViewOptionalParams, boolean useUpdate, int ccCmdDelay) {
		this.cleartool = cleartool;
		this.logger = logger;
		this.view = view;
		this.stgloc = stgloc;
		this.mkViewOptionalParams = mkViewOptionalParams;
		this.useUpdate = useUpdate;
		this.ccCmdDelay = ccCmdDelay;
	}

    /*********************
     ** CHECKOUT METHOD **
     *********************/
    public abstract boolean 
    checkout(@SuppressWarnings("rawtypes") AbstractBuild build, TaskListener listener) 
    throws IOException, InterruptedException, ClearToolError;
    

}
