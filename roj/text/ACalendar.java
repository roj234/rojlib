package roj.text;

import org.jetbrains.annotations.Nullable;
import roj.config.ParseException;
import roj.config.word.ITokenizer;
import roj.config.word.Tokenizer;
import roj.io.IOUtil;

import java.util.TimeZone;

/**
 * @author solo6975
 * @since 2021/6/16 2:48
 */
public class ACalendar {
	public static ACalendar GMT() { return new ACalendar(TimeZone.getTimeZone("GMT")); }
	public static ACalendar Local() { return new ACalendar(TimeZone.getDefault()); }

	public ACalendar copy() { return new ACalendar(zone); }

	public static final int YEAR = 0, MONTH = 1, DAY = 2, HOUR = 3, MINUTE = 4, SECOND = 5, MILLISECOND = 6, DAY_OF_WEEK = 7, REN_YEAR = 8, TOTAL = 9;

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
		boolean renYear = isRenYear(y);

		int daysSinceY = (int) (date - days);

		long daysAfterFeb = days + 31 + 28 + (renYear ? 1 : 0);
		if (date >= daysAfterFeb) {
			daysSinceY += renYear ? 1 : 2;
		}

		int m = 12 * daysSinceY + 373;
		if (m > 0) m /= 367;
		else m = floorDiv(m, 367);

		long monthDays = days + SUMMED_DAYS[m] + (m >= 3 && renYear ? 1 : 0);
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

		buf[REN_YEAR] = renYear ? 1 : 0;

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
				date -= isRenYear(year) ? 1L : 2L;
			}

			if (cache != null && firstDay) {
				cache[2] = year;
				cache[3] = date;
			}

			return date;
		}
	}

	private static boolean isRenYear(int year) { return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0); }
	private static long dayOfYear(int year, int month, int day) { return day + (long) (SUMMED_DAYS[month] + (month > 2 && isRenYear(year) ? 1 : 0)); }
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

	public static int floorDiv(int a, int b) { return a >= 0 ? a / b : (a + 1) / b - 1; }
	public static long divModLss(long a, int b, int[] buf) {
		if (a >= 0) {
			return (a % b) << 54 | (a / b);
		} else {
			long div = (a + 1) / b - 1;
			buf[0] = (int) (a - b * div);
			return div;
		}
	}

	private static final String[]
		UTCWEEK = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"},
		UTCMONTH = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

	public CharList format(String format, long stamp) { return format(format, stamp, IOUtil.getSharedCharBuf()); }
	public CharList format(String format, long stamp, CharList sb) {
		int[] fields = parse(stamp);
		char c;
		for (int i = 0; i < format.length(); i++) {
			switch (c = format.charAt(i)) {
				case 'L': sb.append(fields[REN_YEAR]); break;
				case 'Y': sb.append(fields[YEAR]); break;
				case 'y': sb.append(fields[YEAR] % 100); break;
				case 'd': sb.padNumber(fields[DAY], 2); break;
				case 'j': sb.append(fields[DAY]); break;
				case 'l': sb.append("星期").append(ChinaNumeric.NUMBER[fields[DAY_OF_WEEK]]); break;
				case 'W': sb.append(UTCWEEK[fields[DAY_OF_WEEK]-1]); break;
				case 'w': sb.append(fields[DAY_OF_WEEK]-1); break;
				case 'N': sb.append(fields[DAY_OF_WEEK]); break;
				case 'm': sb.padNumber(fields[MONTH], 2); break;
				case 'x': sb.padNumber(fields[MILLISECOND], 3); break;
				case 'n': sb.append(fields[MONTH]); break;
				case 't': // 本月有几天
					int mth = fields[MONTH];
					if (mth == 2) {
						sb.append(28 + fields[REN_YEAR]);
					} else {
						sb.append(((mth & 1) != 0) == mth < 8 ? 31 : 30);
					}
					break;
				case 'a': sb.append(fields[HOUR] > 11 ? "pm" : "am"); break;
				case 'A': sb.append(fields[HOUR] > 11 ? "PM" : "AM"); break;
				case 'g': // am/pm时间
					int h = fields[HOUR] % 12;
					sb.append(h == 0 ? 12 : h);
					break;
				case 'G': sb.append(fields[HOUR]); break;
				case 'h':
					h = fields[HOUR] % 12;
					sb.padNumber(h == 0 ? 12 : h, 2);
					break;
				case 'H': sb.padNumber(fields[HOUR], 2); break;
				case 'i': sb.padNumber(fields[MINUTE], 2); break;
				case 's': sb.padNumber(fields[SECOND], 2); break;
				case 'O': // timezone offset 2
					if (tzoff(stamp, sb) < 0) sb.append("GMT");
					break;
				case 'P':
					int v = tzoff(stamp, sb);
					if (v < 0) sb.append('Z');
					else sb.insert(v+3, ':');
					break;
				case 'c': format("Y-m-dTH:i:sP", stamp, sb); break;
				case 'M': sb.append(UTCMONTH[fields[MONTH] - 1]); break;
				case 'U': sb.append(stamp / 1000); break;
				default: sb.append(c); break;
			}
		}
		return sb;
	}
	private int tzoff(long stamp, CharList sb) {
		if (zone == null) return -1;
		// 这里曾经有个负号
		int offset = zone.getOffset(stamp);
		if (offset == 0) return -1;

		int pos = sb.length();
		sb.append(offset>0?'+':'-');

		offset /= 60000;

		sb.padNumber(Math.abs(offset / 60), 2);
		sb.padNumber(offset % 60, 2);
		return pos;
	}

	public String toISOString(long millis) { return toISOString(new CharList(), millis).toStringAndFree(); }
	public CharList toISOString(CharList sb, long millis) { return format(millis%1000 != 0 ? "Y-m-dTH:i:s.xP" : "Y-m-dTH:i:sP", millis, sb); }

	public String toRFCString(long millis) { return toRFCString(new CharList(),millis).toStringAndFree(); }
	public CharList toRFCString(CharList sb, long millis) { return format("W, d M Y H:i:s O", millis, sb); }

	public static String toLocalTimeString(long time) {
		ACalendar cal = new ACalendar();
		return cal.format("Y-m-d H:i:s.x", time, new CharList()).append(" (").append(cal.zone.getDisplayName()).append(')').toStringAndFree(); }

	/**
	 * parse ISO8601 timestamp
	 * 使用这个函数是为了支持可选的前导零
	 * @see roj.config.word.ITokenizer#ISO8601Datetime
	 * @param seq "2020-07-14T07:59:08+08:00"
	 * @return unix timestamp
	 */
	public static long parseISO8601Datetime(CharSequence seq) {
		ITokenizer tokenizer = new Tokenizer();
		try {
			return tokenizer.init(seq).ISO8601Datetime(true).asLong();
		} catch (ParseException e) {
			throw new IllegalArgumentException(e.toString());
		}
	}
	/**
	 * parse RFC like timestamp
	 * @param seq "Tue, 25 Feb 2022 21:48:10 GMT"
	 * @return unix timestamp
	 */
	@SuppressWarnings("fallthrough")
	public static long parseRFCDate(CharSequence seq) {
		String str = seq.subSequence(0, 3).toString();
		String[] x = UTCWEEK;
		int week = 0;
		while (week < x.length) {
			if (str.equals(x[week++])) {
				break;
			}
		}
		if (week == 8) throw new IllegalArgumentException("Invalid week " + str);
		if (seq.charAt(3) != ',' || seq.charAt(4) != ' ') throw new IllegalArgumentException("分隔符错误");
		int day = TextUtil.parseInt(seq, 5, 7, 10);
		if (seq.charAt(7) != ' ') throw new IllegalArgumentException("分隔符错误");

		str = seq.subSequence(8, 11).toString();
		x = UTCMONTH;
		int month = 0;
		while (month < x.length) {
			if (str.equals(x[month++])) {
				break;
			}
		}
		if (month == 13) throw new IllegalArgumentException("Invalid month " + str);

		if (seq.charAt(11) != ' ') throw new IllegalArgumentException("分隔符错误");

		int year = TextUtil.parseInt(seq, 12, 16, 10);
		if (seq.charAt(16) != ' ') throw new IllegalArgumentException("分隔符错误");
		int h = TextUtil.parseInt(seq, 17, 19, 10);
		if (seq.charAt(19) != ':') throw new IllegalArgumentException("分隔符错误");
		int m = TextUtil.parseInt(seq, 20, 22, 10);
		if (seq.charAt(22) != ':') throw new IllegalArgumentException("分隔符错误");
		int s = TextUtil.parseInt(seq, 23, 25, 10);
		if (seq.charAt(25) != ' ') throw new IllegalArgumentException("分隔符错误");

		if (h > 23) throw new IllegalArgumentException("你一天" + h + "小时");
		if (m > 59) throw new IllegalArgumentException("你一小时" + m + "分钟");
		if (s > 59) throw new IllegalArgumentException("你一分钟" + s + "秒");

		long a = (daySinceAD(year, month, day, null) - GREGORIAN_OFFSET_DAY) * 86400000L + h * 3600000 + m * 60000 + s * 1000;

		int i = -1;
		switch (seq.charAt(26)) {
			case '-':
				i = 1;
			case '+':
				i *= TextUtil.parseInt(seq, 27, 30, 10);
				int d = i % 100;
				if (d < -59 || d > 59) throw new IllegalArgumentException("你一小时" + d + "分钟");
				a += 60000 * d;

				d = i / 100;
				if (d < -23 || d > 23) throw new IllegalArgumentException("你一天" + d + "小时");
				a += 3600000 * d;

				break;
			case 'G':
				if (seq.charAt(27) != 'M' || seq.charAt(28) != 'T') throw new IllegalArgumentException("分隔符错误");
				break;
			default:
				throw new IllegalArgumentException("分隔符错误");
		}

		return a;
	}

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