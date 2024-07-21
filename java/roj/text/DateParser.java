package roj.text;

import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;

import java.util.TimeZone;

import static roj.text.ACalendar.UTCMONTH;
import static roj.text.ACalendar.UTCWEEK;

/**
 * @author Roj234
 * @since 2024/5/6 08:26
 */
public class DateParser {
	private final int[] formats;
	private final String[] chars;
	private final int method;

	public static DateParser create(String format) throws ParseException {
		IntList formats = new IntList();
		SimpleList<String> formatChars = new SimpleList<>();

		Tokenizer wr = new Tokenizer().literalEnd(MyBitSet.from("'\" ")).init(format);
		while (wr.hasNext()) {
			Word w = wr.next();
			if (w.type() == Word.LITERAL) {
				String val = w.val();
				Int2IntMap.Entry entry = STRATEGIES.getEntry(val.charAt(0) | (val.length() << 16));
				if (entry == null) throw wr.err("unknown strategy "+val);
				int strategy = entry.getIntValue();
				formats.add(strategy);
			} else if (w.type() == Word.STRING) {
				formats.add(-1);
				formatChars.add(w.val());
			} else {
				throw wr.err("未预料的类型");
			}
		}

		return new DateParser(formats.toArray(), formatChars.toArray(new String[formatChars.size()]));
	}

	public DateParser(int[] formats, String[] chars) {
		this.formats = formats;
		this.chars = chars;
		this.method = checkMethod(formats);
	}

	private int checkMethod(int[] formats) {
		int bitset = 0;
		for (int f : formats) {
			bitset |= 1 << f;
		}

		int weekday_accept = 0b11_111000000;
		int weekday_ignore = 0b111_1100;

		int method = 0;
		if (Integer.bitCount(bitset&weekday_ignore) < Integer.bitCount(bitset&weekday_accept)) {
			method |= F_YWD;
		}
		if ((bitset & (1 << 16)) != 0) {
			method |= F_AMPM;
		}
		return method;
	}

	private static final int F_YWD = 1, F_AMPM = 2;

	public void setTimeZone(TimeZone timeZone) {this.timeZone = timeZone;}
	public void setCentury(int century) {this.century = century;}
	public void setOptional(boolean optional) {this.optional = optional;}

	private TimeZone timeZone;
	private int century = 19;
	private boolean optional;

	public long parse(CharList sb) {
		int charI = 0;
		TimeZone tz = null;
		// POS YMD HIS MS APM Week DOW fixedTZOff
		int[] cal = new int[] {
			0,
			1900, 1, 1,
			0, 0, 0, 0,
			0, 1, 1, -1
		};
		loop:
		for (int id : formats) {
			switch (id) {
				case -1 -> {
					String s = chars[charI++];
					if (!sb.regionMatches(cal[0], s)) {
						throw new IllegalArgumentException("Delim error");
					}
					cal[0] += s.length();
				}
				case 0 -> cal[1] = dateNum(sb, cal, 4, 0, 9999);
				case 1 -> cal[1] = century * 100 + dateNum(sb, cal, 2, 0, 99);
				case 2, 3 -> cal[2] = dateNum(sb, cal, 2, 1, 12);
				case 4 -> {
					for (int i = 0; i < UTCMONTH.length;) {
						String month = UTCMONTH[i++];
						if (sb.regionMatches(cal[0], month)) {
							cal[2] = i;
							continue loop;
						}
					}
					throw new IllegalArgumentException("Month error");
				}
				case 5, 6 -> cal[3] = dateNum(sb, cal, 2, 1, 31);
				case 7 -> cal[10] = dateNum(sb, cal, 2, 1, 7);
				case 8 -> {
					for (int i = 0; i < UTCWEEK.length;) {
						String week = UTCWEEK[i++];
						if (sb.regionMatches(cal[0], week)) {
							cal[10] = i;
							continue loop;
						}
					}
					throw new IllegalArgumentException("Week error");
				}
				case 9 -> {
					if (sb.regionMatches(cal[0], "星期")) {
						//cal[10] = i;
						continue loop;
					}
					throw new IllegalArgumentException("Week error");
				}
				case 10, 11 -> cal[9] = dateNum(sb, cal, 2, 1, 42);
				case 12, 13, 14, 15 -> cal[4] = dateNum(sb, cal, 2, 0, id > 13 ? 11 : 23);
				case 16 -> {
					String s = sb.substring(cal[0], cal[0] += 2);
					if (s.equalsIgnoreCase("am")) {
						cal[8] = 1;
					} else if (s.equalsIgnoreCase("pm")) {
						cal[8] = 2;
					} else {
						throw new IllegalArgumentException("AM/PM error");
					}
				}
				case 17, 18 -> cal[5] = dateNum(sb, cal, 2, 0, 59);
				case 19, 20 -> cal[6] = dateNum(sb, cal, 2, 0, 59);
				case 21, 22 -> cal[7] = dateNum(sb, cal, 3, 0, 999);
				case 23 -> {
					int i = sb.charAt(cal[0]);
					if (i == 'Z' || i == 'z') {
						cal[0]++;
						cal[11] = 0;
					} else if (i == '+' || i == '-') {
						int fixTzOff = dateNum(sb, cal, 2, 0, 99) * 60;
						char td = sb.charAt(cal[0]);
						if (td == ':' || td == '.') {
							cal[0]++;
							td = sb.charAt(cal[0]);
						}
						if (td >= '0' && td <= '9') fixTzOff += dateNum(sb, cal, 2, 0, 59);
						cal[11] = fixTzOff;
					} else if (i == 'G' && sb.regionMatches(i, "GMT")) {
						cal[0] += 3;
						cal[11] = 0;
					}
				}
				case 24 -> {
					int i = cal[0];
					int prevI = i;
					while (i < sb.length()) {
						char c = sb.charAt(i);
						if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c =='/')) break;
						i++;
					}

					if (i == prevI) throw new IllegalArgumentException("无效的时区Str");
					tz = TimeZone.getTimeZone(sb.substring(prevI, i));
				}
			}
			if (optional && cal[0] == sb.length()) break;
		}

		if ((method&F_AMPM) != 0 && cal[11] == 2) cal[4] += 12;

		long stamp = (ACalendar.daySinceAD(cal[1], cal[2], cal[3], null) - ACalendar.GREGORIAN_OFFSET_DAY);
		if ((method&F_YWD) != 0) stamp += ((cal[9] - 1) * 7L + (cal[10] - 1));

		stamp *= 86400000L;

		stamp += cal[4] * 3600000L;
		stamp += cal[5] * 60000L;
		stamp += cal[6] * 1000L;
		stamp += cal[7];
		if (tz != null) stamp -= tz.getOffset(stamp);
		else if (cal[11] != -1) stamp -= cal[11];
		else if (timeZone != null) stamp -= timeZone.getOffset(stamp);

		return stamp;
	}
	private static int dateNum(CharSequence sb, int[] pos, int maxLen, int min, int max) {
		int i = pos[0];
		int prevI = i;
		while (i < sb.length()) {
			if (!Tokenizer.NUMBER.contains(sb.charAt(i))) break;
			i++;
		}

		if (i-prevI <= 0 || i-prevI > maxLen) throw new IllegalArgumentException("错误的时间范围");

		int num = TextUtil.parseInt(sb, prevI, i);
		if (num < min || num > max) throw new IllegalArgumentException("错误的时间范围");

		pos[0] = i;
		return num;
	}

	/**
	 * parse ISO8601 timestamp
	 * 使用这个函数是为了支持可选的前导零
	 * @see Tokenizer#ISO8601Datetime
	 * @param seq "2020-07-14T07:59:08+08:00"
	 * @return unix timestamp
	 */
	public static long parseISO8601Datetime(CharSequence seq) {
		try {
			return new Tokenizer().init(seq).ISO8601Datetime(true).asLong();
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
		int day = TextUtil.parseInt(seq, 5, 7);
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

		int year = TextUtil.parseInt(seq, 12, 16);
		if (seq.charAt(16) != ' ') throw new IllegalArgumentException("分隔符错误");
		int h = TextUtil.parseInt(seq, 17, 19);
		if (seq.charAt(19) != ':') throw new IllegalArgumentException("分隔符错误");
		int m = TextUtil.parseInt(seq, 20, 22);
		if (seq.charAt(22) != ':') throw new IllegalArgumentException("分隔符错误");
		int s = TextUtil.parseInt(seq, 23, 25);
		if (seq.charAt(25) != ' ') throw new IllegalArgumentException("分隔符错误");

		if (h > 23) throw new IllegalArgumentException("你一天" + h + "小时");
		if (m > 59) throw new IllegalArgumentException("你一小时" + m + "分钟");
		if (s > 59) throw new IllegalArgumentException("你一分钟" + s + "秒");

		long a = (ACalendar.daySinceAD(year, month, day, null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000L + h * 3600000L + m * 60000L + s * 1000L;

		int i = -1;
		switch (seq.charAt(26)) {
			case '-':
				i = 1;
			case '+':
				i *= TextUtil.parseInt(seq, 27, 31);
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

	private static final Int2IntMap STRATEGIES = new Int2IntMap(32);
	private static void reg(char name, int width, int id) {STRATEGIES.putInt(name | (width << 16), id);}
	static {
		reg('Y', 4, 0); // year
		reg('Y', 2, 1); // length-2 year
		reg('M', 1, 2); // Unpadded month
		reg('M', 2, 3); // Padded month
		reg('M', 3, 4); // English month
		reg('D', 1, 5); // Unpadded day
		reg('D', 2, 6); // Padded day

		reg('W', 1, 7); // Number day of week
		reg('W', 3, 8); // English day of week
		reg('W', 4, 9); // Chinese day of week

		reg('w', 1, 10); // Week of the year
		reg('w', 2, 11); // Week of the year

		reg('H', 1, 12); // Unpadded 24 hour
		reg('H', 2, 13); // Padded 24 hour

		reg('h', 1, 14); // Unpadded 12 hour
		reg('h', 2, 15); // Padded 12 hour
		reg('a', 1, 16); // AM/PM

		reg('i', 1, 17); // Unpadded minute
		reg('i', 2, 18); // Padded minute
		reg('s', 1, 19); // Unpadded second
		reg('s', 2, 20); // Padded second

		reg('x', 1, 21); // Unpadded millisecond
		reg('x', 3, 22); // Padded millisecond

		reg('Z', 1, 23); // +- 00:00 Timezone
		reg('z', 1, 24); // String Timezone
	}
}