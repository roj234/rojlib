package roj.text;

import roj.WillChange;
import roj.io.IOUtil;

import java.util.TimeZone;

/**
 * 将时间戳解析出年月日等信息，并可转换为字符串.
 * @see DateFormat
 * @author solo6975
 * @since 2021/6/16 2:48
 */
public final class DateTime {
	public static final int
			YEAR = 0, MONTH = 1, DAY = 2, HOUR = 3, MINUTE = 4, SECOND = 5, MILLISECOND = 6, LEAP_YEAR = 7,
			DAY_OF_WEEK = 8, DAY_OF_YEAR = 9, WEEK_OF_MONTH = 10, WEEK_OF_YEAR = 11, ISO_WEEK_OF_YEAR = 12,
			CORE_FIELD_COUNT = 8, AUXILIARY_FIELD_COUNT = 13;

	private final int[] fields = new int[AUXILIARY_FIELD_COUNT];
	private final TimeZone zone;

	public static DateTime GMT() {return new DateTime(null);}
	public static DateTime local() {return new DateTime(TimeZone.getDefault());}
	public static DateTime forTimezone(TimeZone tz) {return new DateTime(tz);}

	private DateTime(TimeZone tz) {zone = tz;}

	public int[] parse(long timestamp) {return of(timestamp, fields);}
	public static int[] now() {return of(System.currentTimeMillis());}
	public static int[] of(long timestamp) {return of(timestamp, new int[AUXILIARY_FIELD_COUNT]);}
	public static int[] of(long timestamp, int[] fields) {
		if (fields.length < CORE_FIELD_COUNT) throw new ArrayIndexOutOfBoundsException(CORE_FIELD_COUNT);

		int totalDays = Math.toIntExact(timeOf(timestamp, fields)) + UNIX_ZERO;
		if (totalDays < MINIMUM_GREGORIAN_DAY) throw new ArithmeticException("DateTime does not support time < 1582/8/15");

		int year = fields[YEAR] = yearSinceAD(totalDays);
		int firstDayOfYear = daySinceAD(year, 1, 1);
		int daysOfYear = totalDays - firstDayOfYear;

		boolean leapYear;
		if (daysOfYear < 0) {
			fields[YEAR] --;
			fields[MONTH] = 12;
			fields[DAY] = 32 + daysOfYear;

			daysOfYear += 366;
			leapYear = isLeapYear(year);
		} else {
			leapYear = isLeapYear(year);
			// 至少3月1日
			if (daysOfYear >= 59/*SUMMED_DAYS[3]*/ + (leapYear ? 1 : 0)) {
				daysOfYear += leapYear ? 1 : 2;
			}

			int month = fields[MONTH] = (12 * daysOfYear + 373) / 367;

			int monthDays = firstDayOfYear + SUMMED_DAYS[month];
			if (month > 2 && leapYear) monthDays++;
			fields[DAY] = (totalDays - monthDays) + 1;
		}

		fields[LEAP_YEAR] = leapYear ? 1 : 0;

		if (fields.length > CORE_FIELD_COUNT) {
			fields[DAY_OF_YEAR] = daysOfYear;

			int dayOfWeek = (totalDays - 1) % 7;
			fields[DAY_OF_WEEK] = dayOfWeek + 1;

			int firstDayOfMonth = totalDays - (fields[DAY] - 1);
			int firstDayOfWeekMonth = (firstDayOfMonth - 1) % 7;
			fields[WEEK_OF_MONTH] = (fields[DAY] - 1 + firstDayOfWeekMonth) / 7 + 1;

			int firstDayOfWeekYear = (firstDayOfYear - 1) % 7;
			fields[WEEK_OF_YEAR] = (daysOfYear - 1 + firstDayOfWeekYear) / 7 + 1;

			int isoWeek;
			while (true) {
				// 今年第一个星期四
				int jan4th = daySinceAD(year, 1, 4);
				int dowJan4 = jan4th % 7;
				int firstThursdayOfYear = jan4th + (3 - dowJan4);

				// 最近的下一个星期四
				int vme50day = totalDays + (3 - dayOfWeek);

				isoWeek = ((vme50day - firstThursdayOfYear) / 7) + 1;

				if (vme50day >= firstThursdayOfYear) {
					if (isoWeek == 53) {
						// 处理跨年周的特殊情况
						int nextJan4th = daySinceAD(year + 1, 1, 4);
						int nextDowJan4 = nextJan4th % 7;
						int nextFirstThursday = nextJan4th + (3 - nextDowJan4);
						if (vme50day >= nextFirstThursday) {
							year++;
							isoWeek = 1;
						}
					}

					break;
				}
				// 属于上一年的周，这个循环最多执行两次
				else year--;
			}

			// format: 202253
			fields[ISO_WEEK_OF_YEAR] = year * 100 + isoWeek;
		}

		return fields;
	}
	//region 时间戳解析为日历字段辅助函数
	private static final int MINIMUM_GREGORIAN_DAY = 577736; // Fixed day of 1582/8/15
	private static final int UNIX_ZERO = 719163; // Fixed day of 1970/1/1 (Gregorian)

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

	private static long timeOf(long date, int[] fields) {
		long q;

		if (date >= 0) {
			q = date / 1000;
			fields[MILLISECOND] = (int) (date - q * 1000);
			date = q;

			q = date / 60;
			fields[SECOND] = (int) (date - q * 60);
			date = q;

			q = date / 60;
			fields[MINUTE] = (int) (date - q * 60);
			date = q;

			q = date / 24;
		} else {
			q = (date + 1) / 1000 - 1;
			fields[MILLISECOND] = (int) (date - q * 1000);
			date = q;

			q = (date + 1) / 60 - 1;
			fields[SECOND] = (int) (date - q * 60);
			date = q;

			q = (date + 1) / 60 - 1;
			fields[MINUTE] = (int) (date - q * 60);
			date = q;

			q = (date + 1) / 24 - 1;
		}
		fields[HOUR] = (int) (date - q * 24);
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
	 * 每月的天数
	 */
	private static final int[] SUMMED_DAYS = new int[] {-30, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
	private static int daySinceAD(int year, int month, int day) {
		int yearm1 = year-1;
		return yearm1*365 + yearm1/4 - yearm1/100 + yearm1/400 // with leap days
				+ day + (SUMMED_DAYS[month] + (month > 2 && isLeapYear(year) ? 1 : 0));
	}

	private static boolean isLeapYear(int year) { return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0); }

	private static int getMonthDays(int[] fields) {
		int month = fields[MONTH];
		return month == 2 ? 28 + fields[LEAP_YEAR] : ((month & 1) != 0) == month < 8 ? 31 : 30;
	}
	//endregion
	/**
	 * 加减之后修正日历字段
	 */
	public static int[] normalize(@WillChange int[] fields) {
		int time, advance;

		time = fields[MILLISECOND];
		advance = time / 1000;
		if (time < 0) advance--;
		time -= 1000 * advance;
		fields[MILLISECOND] = time;

		time = fields[SECOND] + advance;
		advance = time / 60;
		if (time < 0) advance--;
		time -= 60 * advance;
		fields[SECOND] = time;

		time = fields[MINUTE] + advance;
		advance = time / 60;
		if (time < 0) advance--;
		time -= 60 * advance;
		fields[MINUTE] = time;

		time = fields[HOUR] + advance;
		advance = time / 24;
		if (time < 0) advance--;
		time -= 24 * advance;
		fields[HOUR] = time;

		time = fields[DAY] + advance;
		int monthDays = getMonthDays(fields);
		while (time > monthDays) {
			if (fields[MONTH] == 12) {
				fields[MONTH] = 1;
				fields[LEAP_YEAR] = isLeapYear(++fields[YEAR]) ? 1 : 0;
			} else {
				fields[MONTH]++;
			}
			time -= monthDays;
			monthDays = getMonthDays(fields);
		}
		while (time < 0) {
			if (fields[MONTH] == 1) {
				fields[MONTH] = 12;
				fields[LEAP_YEAR] = isLeapYear(--fields[YEAR]) ? 1 : 0;
			} else {
				fields[MONTH]--;
			}
			time += monthDays;
			monthDays = getMonthDays(fields);
		}
		fields[DAY] = time;

		time = fields[MONTH];
		advance = (time - (time > 0 ? 1 : 11)) / 12;
		time -= 12 * advance;
		fields[MONTH] = time;

		fields[YEAR] += advance;

		fields[DAY_OF_WEEK] = -1;
		fields[DAY_OF_YEAR] = -1;
		fields[LEAP_YEAR] = isLeapYear(fields[YEAR]) ? 1 : 0;

		return fields;
	}

	public static long toTimeStamp(int[] fields) {
		return DateTime.daySinceUnixZero(fields[YEAR], fields[MONTH], fields[DAY]) * 86400000L + fields[HOUR] * 3600000L + fields[MINUTE] * 60000L + fields[SECOND] * 1000L + fields[MILLISECOND];
	}
	//region 日期格式化相关函数
	static final String[]
		UTCWEEK = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"},
		UTCMONTH = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

	@Deprecated
	public CharList format(String format, long stamp) { return format(format, stamp, IOUtil.getSharedCharBuf()); }
	@Deprecated
	public CharList format(String format, long stamp, CharList sb) {
		if (zone != null) stamp += zone.getOffset(stamp);
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

				case 't' -> sb.append(getMonthDays(fields));

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
					if (DateFormat.tzoff(zone, stamp, sb)) sb.append(c == 'P' ? "Z" : "GMT");
				}

				case 'c' -> format(ISO_Format_Sec, stamp, sb);
				default -> sb.append(c);
			}
		}
		return sb;
	}

	public static final String ISO_Format_Millis = "Y-m-dTH:i:s.xP", ISO_Format_Sec = "Y-m-dTH:i:sP";
	public String toISOString(long millis) { return toISOString(new CharList(), millis).toStringAndFree(); }
	public CharList toISOString(CharList sb, long millis) { return format(millis%1000 != 0 ? ISO_Format_Millis : /*millis%86400000 == 0 ? "Y-m-d" : */ISO_Format_Sec, millis, sb); }

	public String toRFCString(long millis) { return toRFCString(new CharList(),millis).toStringAndFree(); }
	public CharList toRFCString(CharList sb, long millis) { return format("W, d M Y H:i:s O", millis, sb); }

	public static String toLocalTimeString(long time) {
		var cal = local();
		return cal.format("Y-m-d H:i:s.x (l)", time, new CharList()).append(" (").append(cal.zone!=null?cal.zone.getDisplayName():"GMT").append(')').toStringAndFree(); }
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