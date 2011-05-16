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
package hudson.plugins.clearcase.changelog;

import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.objects.AffectedFile;
import hudson.plugins.clearcase.objects.BaseChangeLogEntry;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

/**
 * ClearCase change log parser.
 * 
 * @author Erik Ramfelt
 * @author Robin Jarry : 2010-08-04 -> removed rendundant code
 */
public class BaseChangeLogParser extends ChangeLogParser {

    @Override
	public ChangeLogSet<BaseChangeLogEntry> parse(@SuppressWarnings("rawtypes") 
	                                              AbstractBuild build, 
												  File changelogFile)
			throws IOException, SAXException 
	{
        FileInputStream fileInputStream = new FileInputStream(changelogFile);
        
        List<BaseChangeLogEntry> history = new ArrayList<BaseChangeLogEntry>();

        // Parse the change log file.
        Digester digester = new Digester();
        digester.setClassLoader(ClearCaseChangeLogSet.class.getClassLoader());
        digester.push(history);
        
        digester.addObjectCreate("*/entry", BaseChangeLogEntry.class);
        digester.addBeanPropertySetter("*/entry/date", "dateStr");
        digester.addBeanPropertySetter("*/entry/comment");
        digester.addBeanPropertySetter("*/entry/user");
        digester.addBeanPropertySetter("*/entry/file");
        digester.addBeanPropertySetter("*/entry/action");
        digester.addBeanPropertySetter("*/entry/version");
        
        digester.addObjectCreate("*/entry/element", AffectedFile.class);
        digester.addBeanPropertySetter("*/entry/element/file", "name");
        digester.addBeanPropertySetter("*/entry/element/version");
        digester.addBeanPropertySetter("*/entry/element/action");
        digester.addBeanPropertySetter("*/entry/element/operation");
        digester.addSetNext("*/entry/element","addFile");
        
        digester.addSetNext("*/entry", "add");
        digester.parse(fileInputStream);
        fileInputStream.close();

        return new BaseChangeLogSet(build, history);
    }
}
