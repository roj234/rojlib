package roj.text;

import org.jetbrains.annotations.Nullable;
import roj.io.IOUtil;

import java.util.TimeZone;

/**
 * @author solo6975
 * @since 2021/6/16 2:48
 */
public final class ACalendar {
	public static ACalendar GMT() { return new ACalendar(null); }
	public static ACalendar Local() { return new ACalendar(TimeZone.getDefault()); }

	public ACalendar copy() { return new ACalendar(zone); }

	public static final int YEAR = 0, MONTH = 1, DAY = 2, HOUR = 3, MINUTE = 4, SECOND = 5, MILLISECOND = 6, DAY_OF_WEEK = 7, LEAP_YEAR = 8, TOTAL = 9;

	private final int[] buf = new int[TOTAL];
	private final long[] cache = new long[4];
	private TimeZone zone;

	public ACalendar() { this(TimeZone.getDefault()); }
	public ACalendar(TimeZone tz) { zone = tz; }

	public TimeZone getTimezone() { return zone; }
	public void setTimezone(TimeZone tz) { zone = tz; }

	public int[] parse(long unix) {
		if (zone != null) unix += zone.getOffset(unix);
		return parse(unix, buf, cache);
	}
	public static int[] parse1(long unix) { return parse(unix, new int[TOTAL], null); }

	private static int[] parse(long date, int[] buf, long[] cache) {
		if (buf.length < 9) throw new ArrayIndexOutOfBoundsException(6);

		date = His(date, buf);
		date += GREGORIAN_OFFSET_DAY;

		if (date < MINIMUM_GREGORIAN_DAY) throw new ArithmeticException("ACalendar does not support time < 1582/8/15");

		int y = buf[YEAR] = yearSinceUnix(date);
		long days = daySinceAD(y, 1, 1, cache);
		boolean leapYear = isLeapYear(y);

		int daysSinceY = (int) (date - days);

		long daysAfterFeb = days + 31 + 28 + (leapYear ? 1 : 0);
		if (date >= daysAfterFeb) {
			daysSinceY += leapYear ? 1 : 2;
		}

		int m = 12 * daysSinceY + 373;
		if (m > 0) m /= 367;
		else m = floorDiv(m, 367);

		long monthDays = days + SUMMED_DAYS[m] + (m >= 3 && leapYear ? 1 : 0);
		buf[DAY] = (int) (date - monthDays) + 1;

		if (m == 0) {
			// fail safe
			buf[YEAR]--;
			buf[MONTH] = 12;
			buf[DAY]++;
		} else {
			buf[MONTH] = m;
		}

		buf[DAY_OF_WEEK] = dayOfWeek(date - 1);

		buf[LEAP_YEAR] = leapYear ? 1 : 0;

		return buf;
	}
	private static long His(long date, int[] buf) {
		if (date > 0) {
			buf[MILLISECOND] = (int) (date % 1000);
			date /= 1000;

			buf[SECOND] = (int) (date % 60);
			date /= 60;

			buf[MINUTE] = (int) (date % 60);
			date /= 60;

			buf[HOUR] = (int) (date % 24);
			date /= 24;
		} else {
			date = divModLss(date, 1000, buf);
			buf[MILLISECOND] = buf[0];

			date = divModLss(date, 60, buf);
			buf[SECOND] = buf[0];

			date = divModLss(date, 60, buf);
			buf[MINUTE] = buf[0];

			date = divModLss(date, 24, buf);
			buf[HOUR] = buf[0];
		}

		return date;
	}
	private static int yearSinceUnix(long date) {
		int years = 400 * (int) (date / 146097L);
		int datei = (int) (date % 146097L);

		int r100;
		years += 100 * (r100 = datei / 36524);
		datei = datei % 36524;

		years += 4 * (datei / 1461);

		years += (datei % 1461) / 365;

		if (r100 != 4 && years != 4) {
			++years;
		}

		return years;
	}

	/***
	 * 计算公元零年后过去的日子
	 * @return 天数
	 */
	public static long daySinceAD(int year, int month, int day, @Nullable long[] cache) {
		boolean firstDay = month == 1 && day == 1;
		if (cache != null && cache[2] == year) {
			return cache[3] + (firstDay ? 0 : dayOfYear(year, month, day) - 1);
		} else {
			int i = year - 1970;
			if (i >= 0 && i < CACHED_YEARS.length) {
				long yearTS = CACHED_YEARS[i];
				if (cache != null) {
					cache[2] = year;
					cache[3] = yearTS;
				}

				return firstDay ? yearTS : yearTS + dayOfYear(year, month, day) - 1L;
			}

			long longYr = year - 1L;
			long date = day + longYr * 365 + ((367L * month - 362) / 12) // raw year + month + day
				+ longYr / 4L - longYr / 100L + longYr / 400L; // ren days

			if (month > 2) { // feb offset
				date -= isLeapYear(year) ? 1L : 2L;
			}

			if (cache != null && firstDay) {
				cache[2] = year;
				cache[3] = date;
			}

			return date;
		}
	}

	private static boolean isLeapYear(int year) { return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0); }
	private static long dayOfYear(int year, int month, int day) { return day + (long) (SUMMED_DAYS[month] + (month > 2 && isLeapYear(year) ? 1 : 0)); }
	private static int dayOfWeek(long day) { return (int) (day % 7L) + 1; }

	/**
	 * 1970 to 2030
	 */
	private static final int[] CACHED_YEARS = new int[] {719163, 719528, 719893, 720259, 720624, 720989, 721354, 721720, 722085, 722450, 722815, 723181, 723546, 723911, 724276, 724642, 725007, 725372, 725737,
												 726103, 726468, 726833, 727198, 727564, 727929, 728294, 728659, 729025, 729390, 729755, 730120, 730486, 730851, 731216, 731581, 731947, 732312, 732677,
												 733042, 733408, 733773, 734138, 734503, 734869, 735234, 735599, 735964, 736330, 736695, 737060, 737425, 737791, 738156, 738521, 738886, 739252, 739617,
												 739982, 740347, 740713, 741078, 741443, 741808, 742174, 742539, 742904, 743269, 743635, 744000, 744365};
	/**
	 * 每月的天数
	 */
	private static final int[] SUMMED_DAYS = new int[] {-30, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

	public static final int GREGORIAN_OFFSET_DAY = 719163; // Fixed day of 1970/1/ 1 (Gregorian)
	public static final int MINIMUM_GREGORIAN_DAY = 577736; // Fixed day of 1582/8/15

	private static int floorDiv(int a, int b) { return a >= 0 ? a / b : (a + 1) / b - 1; }
	private static long divModLss(long a, int b, int[] buf) {
		if (a >= 0) {
			return (a % b) << 54 | (a / b);
		} else {
			long div = (a + 1) / b - 1;
			buf[0] = (int) (a - b * div);
			return div;
		}
	}

	static final String[]
		UTCWEEK = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"},
		UTCMONTH = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

	public CharList format(String format, long stamp) { return format(format, stamp, IOUtil.getSharedCharBuf()); }
	public CharList format(String format, long stamp, CharList sb) {
		int[] fields = parse(stamp);
		char c;
		for (int i = 0; i < format.length(); i++) {
			switch (c = format.charAt(i)) {
				case 'Y' -> sb.append(fields[YEAR]);
				case 'y' -> sb.append(fields[YEAR] % 100);

				case 'L' -> sb.append(fields[LEAP_YEAR]);

				case 'M' -> sb.append(UTCMONTH[fields[MONTH] - 1]);
				case 'm' -> sb.padNumber(fields[MONTH], 2);
				case 'n' -> sb.append(fields[MONTH]);

				case 't' -> {// 本月有几天
					int mth = fields[MONTH];
					sb.append(mth == 2 ? 28 + fields[LEAP_YEAR] : ((mth & 1) != 0) == mth < 8 ? 31 : 30);
				}

				case 'd' -> sb.padNumber(fields[DAY], 2);
				case 'j' -> sb.append(fields[DAY]);
				case 'D' -> sb.append(stamp / 86400000L);

				case 'l' -> sb.append("星期").append(ChinaNumeric.NUMBER[fields[DAY_OF_WEEK]]);
				case 'W' -> sb.append(UTCWEEK[fields[DAY_OF_WEEK]-1]);
				case 'w' -> sb.append(fields[DAY_OF_WEEK]-1);

				case 'H' -> sb.padNumber(fields[HOUR], 2);
				case 'G' -> sb.append(fields[HOUR]);

				case 'A' -> sb.append(fields[HOUR] > 11 ? "PM" : "AM");
				case 'a' -> sb.append(fields[HOUR] > 11 ? "pm" : "am");

				case 'h' -> {
					int h = fields[HOUR] % 12;
					sb.padNumber(h == 0 ? 12 : h, 2);
				}
				case 'g' -> {// am/pm时间
					int h = fields[HOUR] % 12;
					sb.append(h == 0 ? 12 : h);
				}

				case 'i' -> sb.padNumber(fields[MINUTE], 2);
				case 's' -> sb.padNumber(fields[SECOND], 2);
				case 'x' -> sb.padNumber(fields[MILLISECOND], 3);

				// unix秒时间戳
				case 'U' -> sb.append(stamp / 1000);

				case 'O', 'P' -> {// TimeZone offset
					if (tzoff(stamp, sb)) sb.append(c == 'P' ? "Z" : "GMT");
				}

				case 'c' -> format("Y-m-dTH:i:sP", stamp, sb);
				default -> sb.append(c);
			}
		}
		return sb;
	}
	private boolean tzoff(long stamp, CharList sb) {
		int offset;
		if (zone == null || (offset = zone.getOffset(stamp)) == 0) return true;

		offset /= 60000;

		sb.append(offset>0?'+':'-')
		  .padNumber(Math.abs(offset / 60), 2)
		  .append(':')
		  .padNumber(offset % 60, 2);
		return false;
	}

	public static final String ISO_Format_Millis = "Y-m-dTH:i:s.xP", ISO_Format_Sec = "Y-m-dTH:i:sP";
	public String toISOString(long millis) { return toISOString(new CharList(), millis).toStringAndFree(); }
	public CharList toISOString(CharList sb, long millis) { return format(millis%1000 != 0 ? ISO_Format_Millis : /*millis%86400000 == 0 ? "Y-m-d" : */ISO_Format_Sec, millis, sb); }

	public String toRFCString(long millis) { return toRFCString(new CharList(),millis).toStringAndFree(); }
	public CharList toRFCString(CharList sb, long millis) { return format("W, d M Y H:i:s O", millis, sb); }

	public static String toLocalTimeString(long time) {
		ACalendar cal = new ACalendar();
		return cal.format("Y-m-d H:i:s.x", time, new CharList()).append(" (").append(cal.zone!=null?cal.zone.getDisplayName():"GMT").append(')').toStringAndFree(); }

	private static final int[]
		TIME_DT = {60, 1800, 3600, 86400, 604800, 2592000, 15552000},
		TIME_FACTOR = {60, 1, 60, 24, 7, -2592000, 6};
	private static final String[] TIME_NAME = {" 秒前", " 分前", "半小时前", " 小时前", " 天前", " 周前", " 月前"};

	public String prettyTime(long unix) {
		long diff = System.currentTimeMillis() - unix;
		if (diff == 0) return "现在";
		if (diff < 0) return Long.toString(diff);
		double val = diff;
		boolean flag = false;
		for (int i = 0; i < TIME_DT.length; i++) {
			int time = TIME_DT[i];
			if (diff < time) {
				return flag ? TIME_NAME[i] : Math.round(val) + TIME_NAME[i];
			}
			int dt = TIME_FACTOR[i];
			flag = dt == 1;
			if (dt < 0) {
				val = (double) time / (-dt);
			} else {
				val /= dt;
			}
		}
		return format("Y-m-d H:i:s", unix).toStringAndFree();
	}
}