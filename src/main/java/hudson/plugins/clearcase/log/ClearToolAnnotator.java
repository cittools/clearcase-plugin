package hudson.plugins.clearcase.log;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleAnnotatorFactory;

import java.util.regex.Pattern;

@Extension
public class ClearToolAnnotator extends ConsoleAnnotatorFactory<Object> {

    @Override
    public ConsoleAnnotator<Object> newInstance(Object context) {
        return new ClearToolConsoleAnnotator();
    }

    public static class ClearToolConsoleAnnotator extends ConsoleAnnotator<Object> {
        
        @Override
        public ConsoleAnnotator<Object> annotate(Object context, MarkupText text) {
            try {
                if (((java.lang.Class<?>) context).getName().equals(ClearToolAnnotator.class.getName())) {
                    if (text.findToken(COMMAND_LINE_PATTERN) != null) {
                        text.addMarkup(0, text.length(), "<font color='#0000ff'>", "</font>");
                    }
                    if (text.findToken(PLUGIN_MESSAGE_PATTERN) != null) {
                        text.addMarkup(0, text.length(), "<font color='#00aa00'>", "</font>");
                    }
                    if (text.findToken(CLEARTOOL_ERROR_PATTERN) != null) {
                        text.addMarkup(0, text.length(), "<font color='#ee0000'><b>", "</b></font>");
                    }
                }
            } catch (Exception e) {
                /* pass */
            }
            
            return this;
        }
        
        private static final long serialVersionUID = 1L;

        private static final Pattern COMMAND_LINE_PATTERN = Pattern.compile(">>> ");
        private static final Pattern PLUGIN_MESSAGE_PATTERN = Pattern.compile("\\[ClearCase\\]");
        private static final Pattern CLEARTOOL_ERROR_PATTERN = Pattern.compile("cleartool: Error:");
        
    }

}
