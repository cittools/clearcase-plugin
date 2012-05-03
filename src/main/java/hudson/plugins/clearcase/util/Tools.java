package hudson.plugins.clearcase.util;

import hudson.FilePath;
import hudson.remoting.Callable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class Tools {

	public static final String ILLEGAL_CHARS_VIEW_REX = ".*[\"\\\\/\\(\\)\\[\\]\\'\\^~!#\\*@].*";
	public static final String ILLEGAL_CHARS_STREAM_REX = ".*[\"\\(\\)\\[\\]\\'\\^~!#\\*].*";
	public static final String ILLEGAL_CHARS_BL_REX = ".*[\"\\\\/\\(\\)\\[\\]\\'\\^~!#\\*@].*";;

    public static final SimpleDateFormat CLEARTOOL_DATE_FORMAT = new SimpleDateFormat(
            "dd-MMM-yyyy.HH:mm:ss", Locale.US);
    public static final int MILLISECS_IN_ONE_HOUR = 1000 * 60 * 60;

    

    /**
     * Several date formats to ensure backward compatibility
     */
	public static final DateFormat[] DATE_FORMATS = new DateFormat[] {
			new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), // english date format
			new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"), // french date format
			new SimpleDateFormat("dd/MM/yyyy HH:mm"), // french short date format
			new SimpleDateFormat("yyyyMMdd.HHmmss"), // clearcase date format
			CLEARTOOL_DATE_FORMAT };

	public static Date parseDate(String dateStr) throws ParseException {
		for (DateFormat format : DATE_FORMATS) {
			try {
				return format.parse(dateStr);
			} catch (ParseException e) {
				/* pass */
			}
		}
		throw new ParseException(dateStr + " couldn't be parsed", 0);
	}

	public static String formatCleartoolDate(Date date) {
		int offset = TimeZone.getDefault().getOffset(date.getTime())
				/ MILLISECS_IN_ONE_HOUR;
		String dateStr = CLEARTOOL_DATE_FORMAT.format(date).toLowerCase();
		dateStr += String.format("UTC%+d", offset);
		return dateStr;
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
		if (time <= 0)
			return "0s. ago";

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

	public static String createFileFilterPattern(List<String> loadRules,
			boolean windows) {
		StringBuilder pattern = new StringBuilder("(");

		boolean first = true;
		for (String loadRule : loadRules) {
			if (!loadRule.equals("")) {
				if (first) {
					first = false;
				} else {
					pattern.append("|");
				}
				pattern.append(Tools.convertPathForOS(loadRule, windows)
						.replace("\\", "\\\\"));
			}
		}
		pattern.append(")");

		return pattern.toString();
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


    @SuppressWarnings("serial")
    public static String getHostName(FilePath remotePath) {
        try {
            return remotePath.act(new Callable<String, UnknownHostException>() {
                @Override
                public String call() throws UnknownHostException {
                    return InetAddress.getLocalHost().getHostName();
                }
            });
        } catch (Exception e) {
            return "";
        }
    }
	
	
	
}
