package roj.text;

import roj.io.IOUtil;

import java.util.TimeZone;

/**
 * 将时间戳解析出年月日等信息，并可转换为字符串.
 * @see DateFormat
 * @author solo6975
 * @since 2021/6/16 2:48
 */
public final class DateParser {
	public static final int YEAR = 0, MONTH = 1, DAY = 2, HOUR = 3, MINUTE = 4, SECOND = 5, MILLISECOND = 6, DAY_OF_WEEK = 7, LEAP_YEAR = 8, FIELD_COUNT = 9;

	private final int[] fields = new int[FIELD_COUNT];
	private final TimeZone zone;

	public static DateParser GMT() {return new DateParser(null);}
	public static DateParser local() {return new DateParser(TimeZone.getDefault());}
	public static DateParser forTimezone(TimeZone tz) {return new DateParser(tz);}

	private DateParser(TimeZone tz) {zone = tz;}

	public DateParser copy() {return new DateParser(zone);}

	public int[] parse(long timestamp) {
		if (zone != null) timestamp += zone.getOffset(timestamp);
		return parseGMT(timestamp, fields);
	}
	public static int[] parseGMT(long timestamp) {return parseGMT(timestamp, new int[FIELD_COUNT]);}
	public static int[] parseGMT(long ts, int[] fields) {
		if (fields.length < FIELD_COUNT) throw new ArrayIndexOutOfBoundsException(FIELD_COUNT);

		ts = His(ts, fields);
		ts += UNIX_ZERO;

		if (ts < MINIMUM_GREGORIAN_DAY) throw new ArithmeticException("DateParser does not support time < 1582/8/15");

		int y = fields[YEAR] = yearSinceAD(ts);
		int days = daySinceAD(y, 1, 1);
		int daysInYear = (int) (ts - days);
		boolean leapYear = isLeapYear(y);

		if (daysInYear < 0) {
			fields[YEAR] --;
			fields[MONTH] = 12;
			fields[DAY] = 32 + daysInYear;
		} else {
			// 至少3月1日
			if (daysInYear >= 59/*SUMMED_DAYS[3]*/ + (leapYear ? 1 : 0)) {
				daysInYear += leapYear ? 1 : 2;
			}

			int month = fields[MONTH] = (12 * daysInYear + 373) / 367;

			long monthDays = days + SUMMED_DAYS[month];
			if (month > 2 && leapYear) monthDays++;
			fields[DAY] = (int) (ts - monthDays) + 1;
		}

		fields[DAY_OF_WEEK] = dayOfWeek(ts-1);
		fields[LEAP_YEAR] = leapYear ? 1 : 0;

		return fields;
	}
	//region 时间戳解析为日期相关函数
	private static long His(long date, int[] buf) {
		long q;

		if (date >= 0) {
			q = date / 1000;
			buf[MILLISECOND] = (int) (date - q * 1000);
			date = q;

			q = date / 60;
			buf[SECOND] = (int) (date - q * 60);
			date = q;

			q = date / 60;
			buf[MINUTE] = (int) (date - q * 60);
			date = q;

			q = date / 24;
		} else {
			q = (date + 1) / 1000 - 1;
			buf[MILLISECOND] = (int) (date - q * 1000);
			date = q;

			q = (date + 1) / 60 - 1;
			buf[SECOND] = (int) (date - q * 60);
			date = q;

			q = (date + 1) / 60 - 1;
			buf[MINUTE] = (int) (date - q * 60);
			date = q;

			q = (date + 1) / 24 - 1;
		}
		buf[HOUR] = (int) (date - q * 24);
		date = q;
		return date;
	}
	private static int yearSinceAD(long date) {
		int tmp400 = (int) (date / 146097);
		int datei = (int) (date - tmp400 * 146097L);
		int years = 400 * tmp400;

		int tmp100 = datei / 36524;
		datei -= tmp100 * 36524;
		years += 100 * tmp100;

		int tmp4 = datei / 1461;
		datei -= tmp4 * 1461;
		years += 4 * tmp4;

		years += datei / 365;

		if (tmp100 != 4 && years != 4) years++;

		return years;
	}

	/**
	 * 计算Unix零纪元至year-month-day过去的天数.
	 * 年份使用了si32，所以它会在遥远的未来，而不是“更加遥远”的未来溢出：约588万年
	 * 作为对比，si64的毫秒时间戳将在2.9亿年后溢出
	 * @param year 大于等于1的年数
	 * @param month 从1开始的月数
	 * @param day 从1开始的天数
	 * @return 合计天数
	 */
	public static int daySinceUnixZero(int year, int month, int day) {return daySinceAD(year, month, day) - UNIX_ZERO;}
	private static final int UNIX_ZERO = 719163; // Fixed day of 1970/1/1 (Gregorian)
	/**
	 * 每月的天数
	 */
	private static final int[] SUMMED_DAYS = new int[] {-30, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
	private static int daySinceAD(int year, int month, int day) {
		int yearm1 = year-1;
		return yearm1*365 + yearm1/4 - yearm1/100 + yearm1/400 // with leap days
				+ day + (SUMMED_DAYS[month] + (month > 2 && isLeapYear(year) ? 1 : 0));
	}

	private static boolean isLeapYear(int year) { return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0); }
	private static int dayOfWeek(long day) { return (int) (day % 7L) + 1; }

	private static final int MINIMUM_GREGORIAN_DAY = 577736; // Fixed day of 1582/8/15
	//endregion
	//region 日期格式化相关函数
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
		var cal = local();
		return cal.format("Y-m-d H:i:s.x", time, new CharList()).append(" (").append(cal.zone!=null?cal.zone.getDisplayName():"GMT").append(')').toStringAndFree(); }
	//endregion
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