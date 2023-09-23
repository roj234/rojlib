package roj.config.word;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.concurrent.OperationDone;
import roj.concurrent.Ref;
import roj.config.ParseException;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.IOException;

import static roj.config.word.Word.*;

/**
 * @author Roj234
 * @since 2020/10/31 14:22
 */
public abstract class ITokenizer {
	public static final MyBitSet
		NUMBER = MyBitSet.from("0123456789"),
		WHITESPACE = MyBitSet.from(" \r\n\t\f"), // #12288 Chinese space '　'
		SPECIAL = MyBitSet.from("+-\\/*()!~`@#$%^&_=,<>.?\"':;|[]{}");

	private static final int _AF_ROD = 1;

	protected final CharList found = new CharList(32);
	protected MyBitSet literalEnd = WHITESPACE;

	public int index, prevIndex;

	// for number word
	private Word lastWord;
	private short lastWordId;
	private int lastWordEnd, lastWordPos, marker;
	private String lastWordVal;

	protected CharSequence input;
	protected byte aFlag;

	public ITokenizer() {}

	public ITokenizer init(CharSequence seq) {
		prevIndex = -1;
		index = 0;
		input = seq;
		marker = -3;
		aFlag = (byte) (seq instanceof TextReader ? _AF_ROD : 0);
		return this;
	}

	public final CharSequence getText() {
		return input;
	}

	public void emptyWordCache() {
		if (marker == -1) {
			marker = -3;
			index = prevIndex;
		}
	}

	public final Word next() throws ParseException {
		if (marker == -1) {
			marker = -2;
			// next, read *?, retract
			index = lastWordEnd;
			return lastWord.init(lastWordId, lastWordPos, lastWordVal);
		}

		flushBefore(prevIndex);

		marker = 1;
		prevIndex = index;
		Word w = readWord();
		lastWord = w;
		lastWordEnd = index;
		lastWordId = w.type();
		lastWordPos = w.pos();
		lastWordVal = w.val();
		return w;
	}

	protected final void flushBefore(int i) {
		if ((aFlag&_AF_ROD) != 0) ((TextReader)input).releaseBefore(i);
	}

	/**
	 * restore index to (before) previous nextWord() call
	 */
	public void retractWord() {
		if (marker == -1 || marker == -3) {
			throw new IllegalArgumentException("Unable retract");
		}
		marker = -1;
	}

	// region 转义

	private static final Int2IntMap ADDSLASHES = new Int2IntMap();
	private static final Int2IntMap DESLASHES = new Int2IntMap();
	private static void a(char a, char b) {
		ADDSLASHES.putInt(a, b);
		DESLASHES.putInt(b, a);
	}
	static {
		// 双向
		a('\\', '\\');
		a('\n', 'n');
		a('\r', 'r');
		a('\t', 't');
		a('\b', 'b');
		a('\f', 'f');
		a('\'', '\'');
		a('"', '"');

		// 仅解码
		DESLASHES.putInt('/', -1);
		DESLASHES.putInt('U', -2);
		DESLASHES.putInt('u', -3);

		// \ NEXTLINE
		DESLASHES.putInt('\r', -4);
		DESLASHES.putInt('\n', -5);

		// \377 (oct)
		for (int i = 0; i <= 7; i++) DESLASHES.putInt('0'+i, -6);
	}

	public static String addSlashes(CharSequence key) { return addSlashes(new CharList(), key).toStringAndFree(); }
	public static <T extends Appendable> T addSlashes(T to, CharSequence key) { return addSlashes(key, 0, to, '\0'); }
	public static <T extends Appendable> T addSlashes(CharSequence key, int since, T to, char ignore) {
		try {
			if (since > 0) to.append(key, 0, since);

			int prevI = since;
			for (int i = since; i < key.length(); i++) {
				char c = key.charAt(i);
				int v = ADDSLASHES.getOrDefaultInt(c, 0);
				if (v > 0 && c != ignore) {
					to.append(key, prevI, i).append('\\').append((char) v);
					prevI = i+1;
				} else if (c < 32 || (c >= 127 && c <= 159)) { // CharacterData is not open
					if (i+1 < key.length() && NUMBER.contains(key.charAt(i+1))) {
						String s = Integer.toHexString(c);
						to.append(key, prevI, i).append("\\u");
						int j = s.length();
						while (j++ < 4) to.append('0');
						to.append(s);
					} else {
						String s = Integer.toOctalString(c);
						to.append(key, prevI, i).append('\\').append(s);
					}
					prevI = i+1;
				}
			}

			to.append(key, prevI, key.length());
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return to;
	}

	public static String removeSlashes(CharSequence key) throws ParseException {
		return removeSlashes(key, new StringBuilder()).toString();
	}
	public static <T extends Appendable> T removeSlashes(CharSequence in, T output) throws ParseException {
		int i = 0;

		boolean slash = false;

		try {
			while (i < in.length()) {
				char c = in.charAt(i++);
				if (slash) {
					i = _removeSlash(in, c, output, i);
					slash = false;
				} else {
					if (c == '\\') {
						slash = true;
					} else {
						output.append(c);
					}
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}

		if (slash) throw new ParseException(in, "未终止的 斜杠 (\\)", i, null);
		return output;
	}

	@SuppressWarnings("fallthrough")
	protected final CharList readSlashString(char end, boolean zhuanyi) throws ParseException {
		CharSequence in = input;
		int i = index;

		CharList v = found; v.clear();

		boolean slash = false;

		int prevI = i;
		while (i < in.length()) {
			char c = in.charAt(i++);
			if (slash) {
				v.append(in, prevI, i-2);

				i = _removeSlash(input, c, v, i);

				prevI = i;
				slash = false;
			} else {
				if (end == c) {
					v.append(in, prevI, i-1);
					index = i;
					return v;
				} else {
					if (zhuanyi && c == '\\') {
						slash = true;
					}
				}
			}
		}

		throw err("在 转义字符串 终止前遇到了文件尾", i);
	}

	@SuppressWarnings("fallthrough")
	protected static int _removeSlash(CharSequence in, char c, Appendable out, int i) {
		try {
			int v = DESLASHES.getOrDefaultInt(c, 0);
			if (v == 0) {
				out.append('\\').append(in.charAt(i-1));
				return i;//throw new ParseException(in, "无效的转义 \\" + c, i-1, null);
			}
			if (v > 0) {
				out.append((char) v);
				return i;
			}
			switch (v) {
				case -1: out.append('/'); break;
				case -2: // UXXXXXXXX
					int UIndex = TextUtil.parseInt(in, i, i += 8, 16);
					if (Character.charCount(UIndex) > 1) out.append(Character.highSurrogate(UIndex)).append(Character.lowSurrogate(UIndex));
					else out.append((char) UIndex);
				break;
				case -3: // uXXXX
					int uIndex = TextUtil.parseInt(in, i, i += 4, 16);
					out.append((char) uIndex);
				break;
				case -4: out.append("\r"); if (in.charAt(i) == '\n') i++;
				case -5: out.append("\n");
					while (WHITESPACE.contains(in.charAt(i))) i++;
				break;
				case -6: // oct (000 - 377)
					char d = i == in.length() ? 0 : in.charAt(i);
					int xi = c-'0';
					if (d >= '0' && d <= '7') {
						xi = (xi<<3) + d-'0';
						i++;

						char e = i >= in.length() ? 0 : in.charAt(i);
						if (c <= '3' && e >= '0' && e <= '7') {
							xi = (xi<<3) + e-'0';
							i++;
						}
					}
					out.append((char)xi);
				break;

			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return i;
	}

	// endregion
	// region lexer function

	public final boolean hasNext() {
		return index < input.length();
	}
	public char nextChar() {
		return input.charAt(index++);
	}
	protected final int lookAhead(int i) {
		return index+i >= input.length() ? -1 : input.charAt(index+i);
	}
	protected final char lookahead(MyBitSet c1) {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			if (!WHITESPACE.contains(c)) {
				if (c1.contains(c)) {
					index = i+1;
					return c;
				} else {
					break;
				}
			}
			i++;
		}

		// '顺便' 跳过空白
		index = i;
		return 0;
	}

	public abstract Word readWord() throws ParseException;

	/**
	 * 字面量
	 */
	protected Word readLiteral() throws ParseException {
		CharSequence in = input;
		int i = index;

		int prevI = i;
		while (i < in.length()) {
			int c = in.charAt(i);
			if (literalEnd.contains(c)) break;
			i++;
		}
		if (i == prevI) return eof();

		CharList v = found; v.clear();
		v.append(in, prevI, i);
		index = i;
		return formLiteralClip(v);
	}

	/**
	 * 字面量块
	 */
	protected Word formLiteralClip(CharSequence temp) {
		return formClip(LITERAL, temp);
	}

	/**
	 * @return 其他字符
	 */
	protected Word readSymbol() throws ParseException {
		throw err(getClass().getName() + " 没有覆写readSymbol()并调用了");
	}

	/**
	 * int double
	 *
	 * @param sign 检测符号, 必须有1字符空间,因为当作你已知有个符号
	 *
	 * @return [数字] 块
	 */
	@SuppressWarnings("fallthrough")
	protected Word readDigit(boolean sign) throws ParseException {
		return digitReader(sign, DIGIT_HBO);
	}

	protected Word onInvalidNumber(char value, int i, String reason) throws ParseException {
		ParseException e = err(reason, i);
		index = i;
		throw e;
	}

	protected void onNumberFlow(CharSequence value, short fromLevel, short toLevel) throws ParseException {}

	/**
	 * 获取常量字符
	 */
	protected final Word readConstChar() throws ParseException {
		CharList list = readSlashString('\'', true);
		if (list.length() != 1) throw err("未结束的字符常量");
		return formClip(CHARACTER, list);
	}

	/**
	 * 获取常量字符串
	 */
	protected Word readConstString(char key) throws ParseException {
		return formClip(STRING, readSlashString(key, true));
	}

	/**
	 * 封装词 // 缓存
	 *
	 * @param id 词类型
	 */
	protected Word formClip(short id, CharSequence s) {
		return wd.init(id, prevIndex, s.toString());
	}
	protected final Word eof() {
		return wd.init(EOF, prevIndex, "/EOF");
	}
	protected final Word wd = new Word();

	// endregion
	// region 一些数字相关的工具方法

	private static final MyBitSet
		BIN_NUMBERS = MyBitSet.from("01Ll"),
		OCT_NUMBERS = MyBitSet.from("01234567Ll"),
		HEX_NUMBERS = MyBitSet.from("0123456789ABCDEFabcdefLl"),
		DEC_NUMBERS = MyBitSet.from("0123456789DEFdefLl."),
		REAL_NUMBERS = MyBitSet.from("0123456789DdEeFf"),
		REAL_NUMBERS_AFTER_EXP = MyBitSet.from("0123456789DdFf+-");
	private static final int _NF_HEX = 1, _NF_BIN = 2, _NF_OCT = 3, _NF_END = 4, _NF_UNDERSCORE = 8;
	protected static final int DIGIT_HBO = 1, DIGIT_DFL = 2;

	/**
	 * 6 种数字，尽在掌握
	 */
	@SuppressWarnings("fallthrough")
	protected final Word digitReader(boolean sign, int oFlag) throws ParseException {
		CharSequence in = input;
		int i = index;

		boolean neg = sign && in.charAt(i++) == '-';

		/**
		 *  0     1     2    3
		 * int,double,float,long
		 */
		int type = 0;
		/**
		 * bit 0-1: SubType
		 * bit  =  0   1   2   3
		 * mean = INT HEX BIN OCT
		 *
		 * bit 2: Assert end of number
		 * bit 3: Has any underscore(_)
		 */
		int flag = 0;
		MyBitSet set = DEC_NUMBERS;

		char c = in.charAt(i);
		check1:
		if (c == '0' && i+1 < in.length() && (oFlag & DIGIT_HBO) != 0) {
			c = in.charAt(++i);
			switch (c) {
				case 'X': case 'x': flag = _NF_HEX; set = HEX_NUMBERS; i++; break;
				case 'B': case 'b': flag = _NF_BIN; set = BIN_NUMBERS; i++; break;
				default:
					if (NUMBER.contains(c)) {flag = _NF_OCT; set = OCT_NUMBERS;}
					i--;
					break check1;
			}
			// 检测 0x_ 0b_, 保留 0_ 和 00_
			if (i == in.length()) return onInvalidNumber(c, i, "在解释数字时遇到了文件尾");
			if ((c=in.charAt(i)) == '_' || !set.contains(c)) return onInvalidNumber(c, i, "非法的字符"+c);
		} else if (c == '.') { // .5之类的
			// 检测 .e .d .f
			if (!NUMBER.contains(in.charAt(i))) return onInvalidNumber(c, i, "期待[十进制数字]");
			type = 1; set = REAL_NUMBERS;
		}

		int prevI = i;
		while (true) {
			c = in.charAt(i);

				// 检测位置有误(不在结尾)的 d f l 但是放过hex中的df
			if ((flag & _NF_END) != 0 ||
				// 非法数字
				(!set.contains(c) &&
				c != '_')) {
				if (literalEnd.contains(c)) break;
				return onInvalidNumber(c, i, set.contains(c)?"期待[空白]":"非法的字符"+c);
			}

			switch (c) {
				case 'E': case 'e': // 5e3之类的
					if ((flag&3) == _NF_HEX) break;
					type = 1;
					set = REAL_NUMBERS_AFTER_EXP;
					break;
				case '.':
					type = 1;
					set = REAL_NUMBERS;
					break;
				case 'D': case 'd':
					if ((flag&3) == _NF_HEX) break;
					type = 1;
					flag |= _NF_END;
					break;
				case 'F': case 'f':
					if ((flag&3) == _NF_HEX) break;
					type = 2;
					flag |= _NF_END;
					break;
				case 'L': case 'l':
					type = 3;
					flag |= _NF_END;
					break;
				case '_':
					flag |= _NF_UNDERSCORE;
					break;
				case '+': case '-': // 必须跟在e后面
					c = in.charAt(i-1);
					if (c != 'E' && c != 'e') return onInvalidNumber(c, i, "非法的字符"+c);
					break;
			}
			if (++i == in.length()) break;
		}

		if ((flag & _NF_END) != 0) {
			i--;
			if ((oFlag & DIGIT_DFL) == 0) return onInvalidNumber(c, i, "设置DIGIT_DFL位以启用字母结尾的数字");
		}
		if (i <= prevI) throw err("请在调用前做检查");

		// 结尾
		c = in.charAt(i-1);

		CharList v = found; v.clear();

		// 存在下划线
		if ((flag & _NF_UNDERSCORE) != 0) {
			if (c == '_') return onInvalidNumber(c, i, "非法的下划线");
			while (prevI < i) {
				c = in.charAt(prevI++);
				if (c != '_') v.append(c);
			}
		} else {
			v.append(in, prevI, i);
		}

		// 实数
		if (type == 1 || type == 2) {
			switch (c) {
				case 'E': case 'e':
				case '+': case '-':
					return onInvalidNumber(c, i, "数字在指数后被截断");
			}
		}

		if ((flag & _NF_END) != 0) i++;

		try {
			String represent = neg ? "-".concat(v.toString()) : v.toString();

			Word w;
			// retain SubType
			flag &= 3;
			switch (type) {
				case 0:
					if (flag == 0 && !TextUtil.checkMax(TextUtil.INT_MAXS, v, 0, neg)) {
						if (TextUtil.checkMax(TextUtil.LONG_MAXS, v, 0, neg)) {
							onNumberFlow(v, INTEGER, LONG);
							w = new Word_L(index, parseNumber(v, TextUtil.LONG_MAXS, 10, neg), represent);
						} else {
							onNumberFlow(v, INTEGER, DOUBLE);
							w = new Word_D(DOUBLE, index, Double.parseDouble(represent), represent);
						}
					} else {
						w = new Word_I(index, (int) parseNumber(v, RADIX_MAX[flag][0], RADIX[flag], neg), represent);
					}
					break;
				case 1: w = new Word_D(DOUBLE, index, Double.parseDouble(represent), represent); break;
				case 2: w = new Word_D(FLOAT, index, Float.parseFloat(represent), represent); break;
				case 3: w = new Word_L(index, parseNumber(v, RADIX_MAX[flag][1], RADIX[flag], neg), represent); break;
				default: w = Helpers.nonnull();
			}
			index = i;
			return w;
		} catch (NumberFormatException e) {
			return onInvalidNumber(' ', i, e.getMessage());
		}
	}

	private static final byte[][][] RADIX_MAX = new byte[4][2][];
	private static final byte[] RADIX = new byte[] {10, 16, 2, 8};
	static {
		// n mean see digitReader()
		// RM[n][0] => int max
		// RM[n][1] => long max
		RADIX_MAX[0][0] = TextUtil.INT_MAXS;
		RADIX_MAX[0][1] = TextUtil.LONG_MAXS;

		RADIX_MAX[_NF_HEX][0] = new byte[8];
		RADIX_MAX[_NF_HEX][1] = new byte[16];
		fill(RADIX_MAX[_NF_HEX], 'f');

		RADIX_MAX[_NF_OCT][0] = new byte[16];
		RADIX_MAX[_NF_OCT][1] = new byte[32];
		fill(RADIX_MAX[_NF_OCT], '7');

		RADIX_MAX[_NF_BIN][0] = new byte[32];
		RADIX_MAX[_NF_BIN][1] = new byte[64];
		fill(RADIX_MAX[_NF_BIN], '1');
	}
	private static void fill(byte[][] arr, char num) {
		fill(arr[0], num);
		fill(arr[1], num);
	}
	private static void fill(byte[] a0, char num) {
		int i = a0.length-1;
		// +1是因为他们是(be treated as)unsigned的
		a0[i] = (byte) (num+1);
		i--;
		while (i >= 0) {
			a0[i] = (byte) num;
			i--;
		}
	}
	public static long parseNumber(CharSequence s, byte[] radix_max, int radix, boolean neg) throws NumberFormatException {
		long v = 0;

		// range check done
		if (!TextUtil.checkMax(radix_max, s, 0, neg))
			throw new NumberFormatException("number overflow in range[] " + s);

		int i = 0, len = s.length();
		while (i < len) {
			int digit = Character.digit(s.charAt(i), radix);
			if (digit < 0) throw new NumberFormatException("s["+i+"]='"+s.charAt(i)+"' is not a number: " + s);
			i++;

			v *= radix;
			v += digit;
		}

		return neg ? -v : v;
	}

	public final Word ISO8601Datetime(boolean must) throws ParseException {
		final int i = index;
		CharSequence in = input;
		Ref<String> error = Ref.from();

		try {
			char c;

			int y = dateNum(4,0, error);
			dateDelim('-', i, error);

			int m = dateNum(2,12, error);
			dateDelim('-', i, error);

			int d = dateNum(2,31, error);

			long ts = (ACalendar.daySinceAD(y, m, d, null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000L;

			c = index == in.length() ? 0 : in.charAt(index);
			if (c != 'T' && c != 't' && c != ' ')
				return new Word_L(RFCDATE_DATE, i, ts, in.subSequence(i, index).toString());
			index++;

			y = dateNum(2, 23, error);
			dateDelim(':', i, error);

			m = dateNum(2, 59, error);
			dateDelim(':', i, error);

			d = dateNum(2, 59, error);
			c = index == in.length() ? 0 : in.charAt(index++);

			ts += y * 3600000 + m * 60000 + d * 1000;

			if (c == '.') {
				ts += dateNum(3, 0, error);
				c = index == in.length() ? 0 : in.charAt(index++);
			}

			if (c != '+' && c != '-') {
				int type;
				if (c == 'Z' || c == 'z') {
					type = RFCDATE_DATETIME_TZ;
				} else {
					type = RFCDATE_DATETIME;
					index--;
				}
				return new Word_L(type, i, ts, in.subSequence(i, index).toString());
			}

			y = c == '+' ? -60000 : 60000;

			m = dateNum(2, 23, error);
			dateDelim(':', i, error);

			d = dateNum(2, 59, error);

			ts += (m * 60 + d) * y;
			return new Word_L(RFCDATE_DATETIME_TZ, i, ts, in.subSequence(i, index).toString());
		} catch (OperationDone e) {
			if (must) throw err(error.get(), index);

			index = i;
			return null;
		}
	}

	private void dateDelim(char c, int firstIndex, Ref<String> err) {
		int i = index;
		if (input.charAt(i++) == c) index = i;
		else {
			err.set("错误的分隔符,期待"+c);
			throw OperationDone.INSTANCE;
		}
	}

	private int dateNum(int maxLen, int max, Ref<String> err) {
		int i = index;
		CharSequence in = input;

		int prevI = i;
		while (i < in.length()) {
			if (!NUMBER.contains(in.charAt(i))) break;
			i++;
		}

		index = i;

		if (i-prevI <= 0 || i-prevI > maxLen) {
			err.set("错误的时间范围");
			throw OperationDone.INSTANCE;
		}

		int num = TextUtil.parseInt(in, prevI, i, 10);
		if (max > 0 && num > max) {
			err.set("错误的时间范围");
			throw OperationDone.INSTANCE;
		}

		return num;
	}

	// endregion

	@Override
	public String toString() {
		return "Lexer{" + "index=" + index + ", chars=" + input;
	}

	/**
	 * 忽略 // 或 /* ... *\/ 注释
	 *
	 * @param row add comment to
	 */
	protected final Word javaComment(CharList row) throws ParseException {
		CharSequence in = input;
		int i = index;
		if (i >= in.length()) return eof();

		switch (in.charAt(i++)) {
			case '/':
				index = i;
				return singleLineComment(row);
			case '*':
				index = i;
				return multiLineComment(row, "*/");
			default:
				index--;
				return readSymbol();
		}
	}
	protected final Word singleLineComment(CharList row) {
		CharSequence in = input;
		if (index >= in.length()) return eof();

		int i = TextUtil.gNextCRLF(in, index);
		if (i < 0) i = in.length();

		if (row != null) row.append(in, index, i).append('\n');
		index = i;

		return null;
	}
	protected final Word multiLineComment(CharList row, String end) throws ParseException {
		CharSequence in = input;
		int prevI = index;
		if (prevI >= in.length()) return eof();

		int i = TextUtil.gIndexOf(in, end, prevI, in.length());
		if (i < 0) throw err("在注释结束前遇到了文件尾");

		if (row != null) row.append(in, prevI, i-1).append('\n');
		index = i+end.length();

		return null;
	}

	// region exception

	public final Word except(int type) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		throw err("未预料的: " + w.val() + ", 期待: #" + type);
	}

	public final Word except(int type, String v) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		throw err("未预料的: " + w.val() + ", 期待: " + v);
	}

	public final void unexpected(String got, String expect) throws ParseException {
		throw err("未预料的: " + got + ", 期待: " + expect);
	}

	public final void unexpected(String val) throws ParseException {
		throw err("未预料的'" + val + "'");
	}

	public final ParseException err(String reason, int index) {
		return new ParseException(input, reason, index, null);
	}

	public ParseException err(String reason, Word word) {
		return new ParseException(input, reason + " 在 " + word.val(), word.pos(), null);
	}

	public final ParseException err(String reason) {
		return err(reason, (Throwable) null);
	}

	public ParseException err(String reason, Throwable cause) {
		return new ParseException(input, reason, index-1, cause);
	}

	// endregion
}
