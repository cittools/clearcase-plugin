package hudson.plugins.clearcase.util;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.StringParameterValue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Tools {

    public static final String ILLEGAL_CHARS_VIEW_REX = ".*[\"\\\\/\\(\\)\\[\\]\\'\\^~!#\\*@].*";
    public static final String ILLEGAL_CHARS_STREAM_REX = ".*[\"\\(\\)\\[\\]\\'\\^~!#\\*].*";
    public static final String ILLEGAL_CHARS_BL_REX = ".*[\"\\\\/\\(\\)\\[\\]\\'\\^~!#\\*@].*";;




    /**
     * Several date formats to ensure backward compatibility
     */
    public static final DateFormat[] DATE_FORMATS = new DateFormat[] {
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), // english date format
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"), // french date format
            new SimpleDateFormat("dd/MM/yyyy HH:mm"), // french short date format
            new SimpleDateFormat("yyyyMMdd.HHmmss") // clearcase date format
    };

    public static Date parseDate(String dateStr) throws ParseException {
        for(DateFormat format : DATE_FORMATS) {
            try {
                return format.parse(dateStr);
            } catch (ParseException e) {
                /* pass */
            }
        }
        throw new ParseException(dateStr + " could'nt be parsed", 0);
    }


    public static String convertPathForOS(String path, boolean windows) {

        if (windows) {
            path = path.replaceAll("\\n", "\r\n");
            path = path.replaceAll("\\r\\r\\n", "\r\n");
            path = path.replaceAll("/", "\\\\");
        } else {
            path = path.replaceAll("\\r", "");
            path = path.replaceAll("\\\\", "/");
        }

        return path;
    }

    public static String fmtDuration(long time) {
        if (time <= 0) return "0s. ago";

        long SECOND = 1000;
        long MINUTE = 60 * SECOND;
        long HOUR = 60 * MINUTE;
        long DAY = 24 * HOUR;

        StringBuilder sb = new StringBuilder();

        int temp = (int) (time / DAY);
        if (temp != 0) {
            sb.append(temp + "d. ");
        }

        temp = (int) ((time % DAY) / HOUR);
        if (temp != 0) {
            sb.append(temp + "h. ");
        }

        temp = (int) ((time % HOUR) / MINUTE);
        if (temp != 0) {
            sb.append(temp + "m. ");
        }

        temp = (int) ((time % MINUTE) / SECOND);
        if (temp != 0) {
            sb.append(temp + "s. ");
        }

        sb.append("ago");

        return sb.toString();
    }




    public static String createFileFilterPattern(List<String> loadRules, boolean windows) {
        StringBuilder pattern = new StringBuilder("(");

        boolean first = true;
        for (String loadRule : loadRules) {
            if (!loadRule.equals("")) {
                if (first) {
                    first = false;
                } else {
                    pattern.append("|");
                }
                pattern.append(Tools.convertPathForOS(loadRule, windows).replace("\\", "\\\\"));
            }
        }
        pattern.append(")");

        return pattern.toString();
    }


    public static List<StringParameterValue> getCCParameters(AbstractBuild<?, ?> build) {
        List<CCParametersAction> actions = build.getActions(CCParametersAction.class);

        CCParametersAction ccAction;
        if (actions.isEmpty()) {
            ccAction = new CCParametersAction();
            build.addAction(ccAction);
        } else {
            ccAction = actions.get(0);
        }

        return ccAction.getParameters();
    }

    public static boolean isWindows(FilePath path) {
        // Windows can handle '/' as a path separator but Unix can't,
        // so err on Unix side
        return path.getRemote().indexOf("\\") != -1;
    }

    public static char fileSep(FilePath path) {
        if (isWindows(path)) {
            return '\\';
        } else {
            return '/';
        }
    }


    public static String joinPaths(String path1, String path2, char fileSep) {
        if (path1 == null || path2 == null) {
            return null;
        }
        while (path1.charAt(path1.length() - 1) == fileSep) {
            path1 = path1.substring(0, path1.length() - 1);
        }
        while (path2.charAt(0) == fileSep) {
            path2 = path2.substring(1);
        }

        return path1 + fileSep + path2;
    }



}
