package roj.text;

import roj.collect.Int2IntMap;
import roj.util.Helpers;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/6/26 0026 17:16
 */
public class ChinaNumeric {
	public static final Int2IntMap UNIT_MAP = new Int2IntMap(16);
	public static final Int2IntMap NUMBER_MAP = new Int2IntMap(64);
	static {
		UNIT_MAP.putInt('一', 1);
		UNIT_MAP.putInt('十', 10);
		UNIT_MAP.putInt('拾', 10);
		UNIT_MAP.putInt('百', 100);
		UNIT_MAP.putInt('佰', 100);
		UNIT_MAP.putInt('千', 1000);
		UNIT_MAP.putInt('仟', 1000);
		UNIT_MAP.putInt('万', 10000);
		UNIT_MAP.putInt('亿', 100000000);

		for (int i = 0; i < 10; i++) NUMBER_MAP.put('0'+i, i);
		for (int i = 0; i < 10; i++) NUMBER_MAP.put('０'+i, i);

		char[] arr = "壹贰叁肆伍陆柒捌玖".toCharArray();
		for (int i = 0; i < 9;) NUMBER_MAP.put(arr[i], ++i);

		arr = "一二三四五六七八九".toCharArray();
		for (int i = 0; i < 9;) NUMBER_MAP.put(arr[i], ++i);

		NUMBER_MAP.putInt('○', 0);
		NUMBER_MAP.putInt('〇', 0);
		NUMBER_MAP.putInt('O', 0);
		NUMBER_MAP.putInt('o', 0);
		NUMBER_MAP.putInt('零', 0);

		// 照顾手写输入的人
		NUMBER_MAP.putInt('―', 1);
		NUMBER_MAP.putInt('－', 1);
		NUMBER_MAP.putInt('-', 1);
		NUMBER_MAP.putInt('─', 1);
		NUMBER_MAP.putInt('—', 1);

		NUMBER_MAP.putInt('两', 2);

		// 十一万 => 一十一万
		NUMBER_MAP.putInt('十', 10);
	}

	public static long parse(char[] cbuf) { return parse(cbuf, 0, cbuf.length); }
	public static long parse(char[] cbuf, int off, int end) {
		while (off < end && cbuf[off] == '零') off++;

		fail: {

		switch (end-off) {
			case 0: return 0;
			case 1:
				int iNum = NUMBER_MAP.getOrDefaultInt(cbuf[off], -1);
				if (iNum < 0) break fail;
				return iNum;
			case 2:
				char num = cbuf[off], unit = cbuf[off+1];

				iNum = NUMBER_MAP.getOrDefaultInt(num, -1);
				int iUnit = UNIT_MAP.getOrDefaultInt(unit, -1);

				if (iNum < 0) break fail;
				if (iUnit < 0) {
					iUnit = NUMBER_MAP.getOrDefaultInt(unit, -1);
					if (iNum == 10 && iUnit > 0) return 10+iUnit;// 十二
					break fail;
				}
				return iNum * iUnit;
		}

		for (int j = MAGS.length-1; j >= 0; j--) {
			char mag = MAGS[j];

			int i = off;
			while (i < end) {
				if (cbuf[i] == mag) {
					if (i == 0) break fail;

					char n_num = cbuf[i-1];
					char n_unit = cbuf[i];

					int iNum = NUMBER_MAP.getOrDefaultInt(n_num, -1);
					int iUnit = UNIT_MAP.getOrDefaultInt(n_unit, -1);

					if ((iNum|iUnit) < 0) break fail;

					long left = parse(cbuf, off, i-1);
					long right = parse(cbuf, i+1, end);
					return (left+iNum) * iUnit + right;
				}
				i++;
			}
		}

		// no mags
		}

		throw new NumberFormatException(new CharList().append("for input string: \"").append(cbuf, off, end).append('"').toStringAndFree());
	}

	public static final char[] NUMBER = new char[] {'零', '一', '二', '三', '四', '五', '六', '七', '八', '九'};
	public static final char[] MAGS = new char[] {'十', '百', '千', '万', '亿'};

	public static String toString(long num) {
		CharList sb = new CharList().append(num);
		CharList sb2 = new CharList();
		convertIntegral(sb.list, 0, sb.len, sb2);
		sb._free();
		return sb2.toStringAndFree();
	}
	public static String toString(String num) {
		CharList sb = new CharList().append(num);
		try {
			return toString(sb);
		} finally {
			sb._free();
		}
	}
	public static String toString(CharList sb) {
		CharList sb2 = new CharList();
		convertIntegral(sb.list, 0, sb.len, sb2);
		return sb2.toStringAndFree();
	}

	public static String toCurrencyString(double num) {
		CharList sb = new CharList().append(num);
		if (sb.indexOf("e") > 0 || sb.indexOf("E") > 0) throw new NumberFormatException(sb + " is too large");

		try {
			return toCurrencyString(sb);
		} finally {
			sb._free();
		}
	}
	public static String toCurrencyString(String num) {
		CharList sb = new CharList().append(num);
		try {
			return toCurrencyString(sb);
		} finally {
			sb._free();
		}
	}
	public static String toCurrencyString(CharList sb) {
		int pos = sb.indexOf(".");

		CharList sb2 = new CharList();

		if (pos < 0) {
			convertIntegral(sb.list, 0, sb.len, sb2);
			return sb2.append("元整").toStringAndFree();
		}

		convertIntegral(sb.list, 0, pos, sb2);
		sb2.append("元");
		convertDecimal(sb.list, pos+1, sb.len, sb2);

		return sb2.toStringAndFree();
	}

	public static void convertIntegral(char[] cbuf, int off, int len, Appendable out) {
		try {
			int zeroes = 0;
			while (off < len) {
				int pos = len - off - 1;
				char c = cbuf[off++];

				int mag = pos & 3;

				if (c == '0') {
					zeroes++;
				} else {
					if (zeroes > 0) {
						out.append(NUMBER[0]);
						zeroes = 0;
					}

					out.append(NUMBER[c - '0']);
					if (mag > 0) out.append(MAGS[mag-1]);
				}

				if (mag == 0 && zeroes < 4) {
					if (pos >= 4) out.append(MAGS[2 + pos/4]);
					zeroes = 0;
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	public static void convertDecimal(char[] cbuf, int off, int len, Appendable out) {
		try {
			if (off < len) out.append(NUMBER[cbuf[off++]-'0']).append('角');
			if (off < len) out.append(NUMBER[cbuf[off++]-'0']).append('分');
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
}
