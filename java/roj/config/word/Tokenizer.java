package roj.config.word;

import org.jetbrains.annotations.Range;
import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static roj.config.word.Word.*;

/**
 * @author Roj234
 * @since 2020/10/31 14:22
 */
public class Tokenizer {
	public static final MyBitSet
		NUMBER = MyBitSet.from("0123456789"),
		WHITESPACE = MyBitSet.from(" \r\n\t\f"); // #12288 Chinese space '　'

	private static final int _AF_ROD = 1;

	protected final CharList found = new CharList(32);
	protected MyBitSet literalEnd = WHITESPACE;

	public int index, prevIndex;

	protected CharSequence input;
	protected byte aFlag;

	public Tokenizer() {}

	protected static final int C_WHITESPACE = 1, C_MAY__NUMBER_SIGN = 2, C_NUMBER = 3, C_STRING = 4;
	protected static final Int2IntMap NONE_C2C = new Int2IntMap(), NUMBER_C2C = new Int2IntMap(), SIGNED_NUMBER_C2C = new Int2IntMap();

	static {
		fcFill(NONE_C2C, " \r\n\t\f", C_WHITESPACE);
		fcFill(NONE_C2C, "'\"", C_STRING);
		NUMBER_C2C.putAll(NONE_C2C);
		fcFill(NUMBER_C2C, "0123456789", C_NUMBER);
		SIGNED_NUMBER_C2C.putAll(NUMBER_C2C);
		fcFill(SIGNED_NUMBER_C2C, "+-", C_MAY__NUMBER_SIGN);
	}

	protected static void fcFill(Int2IntMap map, String k, int v) {
		for (int i = 0; i < k.length(); i++) {
			map.putInt(k.charAt(i), v);
		}
	}

	public static Tokenizer arguments() { return new Tokenizer().literalEnd(WHITESPACE); }
	public final List<Word> split(String seq) throws ParseException {
		init(seq);
		List<Word> list = new ArrayList<>();
		while (true) {
			Word w = next();
			if (w.type() == Word.EOF) break;
			list.add(w.copy());
		}
		return list;
	}

	protected TrieTree<Word> tokens;
	protected CharList comment;
	protected Int2IntMap firstChar = SIGNED_NUMBER_C2C;

	// region 构造
	public Tokenizer tokenIds(TrieTree<Word> i) { tokens = i; return this; }
	public Tokenizer literalEnd(MyBitSet i) { literalEnd = i; return this; }

	@Deprecated
	public static TrieTree<Word> generate(CharSequence text) {
		TrieTree<Word> indexes = new TrieTree<>();
		int i = 0;
		o:
		while (i < text.length()) {
			int j = i;
			while (text.charAt(j) != ' ') {
				j++;
				if (j == text.length()) break o;
			}
			String key = IOUtil.getSharedCharBuf().append(text, i, j).toString();

			i = ++j;
			while (text.charAt(i) != '\n') {
				i++;
				if (i == text.length()) break;
			}
			int radix = 10;
			switch (radix) {
				case 10: radix = 0; break;
				case 16: radix = 1; break;
				case 8: radix = 2; break;
				case 2: radix = 3; break;
			}
			int val = (int) parseNumber(text, j, i, radix);

			indexes.put(key, new Word().init(val, 0, key));

			i++;
		}

		return indexes;
	}

	public static void addKeywords(TrieTree<Word> indexes, int begin, String... keywords) {
		for (String kw : keywords) {
			indexes.put(kw, new Word().init(begin++, 0, kw));
		}
	}
	public static void addSymbols(TrieTree<Word> indexes, MyBitSet noLiterals, int begin, String... symbols) {
		for (String kw : symbols) {
			indexes.put(kw, new Word().init(begin++, 0, kw));
			if (noLiterals != null) noLiterals.add(kw.charAt(0));
		}
	}
	public static void markSpecial(TrieTree<Word> indexes, String... tokens) {
		for (String kw : tokens) {
			indexes.get(kw).pos = -1;
		}
	}
	public static void addWhitespace(MyBitSet special) { special.addAll(" \t\r\n\f"); }
	// endregion

	public Tokenizer init(CharSequence seq) {
		prevLN = LN = LNIndex = 0;
		prevIndex = index = 0;
		input = seq;
		aFlag = (byte) (seq instanceof TextReader ? _AF_ROD : 0);
		return this;
	}

	public final CharSequence getText() { return input; }

	// region mark, next, retract and reset
	private Word lastWord;
	private short lwType;
	private int lwBegin, lwEnd;
	private String lwStr;

	private byte seek;
	private short prevSeekPos, seekPos;
	private final SimpleList<Word> prevWords = new SimpleList<>();
	public final void mark() throws ParseException {
		if ((seek&1) != 0) throw new UnsupportedOperationException("嵌套的seek");

		if (seek == 2) {
			prevWords.add(lastWord.copy());
		}
		seek = 1;
		prevSeekPos = seekPos;

	}
	public final void reset() { seekPos = 0; }
	public final void skip() { skip(0); }
	public final void skip(int offset) {
		assert (seek&1) != 0;
		seek = 0;
		int i = seekPos+offset;
		if (i == prevWords.size()) {
			lastWord = prevWords.getLast();
			lwEnd = index;
		}
		prevWords.removeRange(0, i);
		seekPos = 0;
	}
	public final void retract() {
		assert (seek&1) != 0;
		seek = 0;
		seekPos = prevSeekPos;
		if (lastWord == wd) lastWord.init(lwType, lwBegin, lwStr);
	}

	public final Word next() throws ParseException {
		if (seek == 2) {
			seek = 0;
			index = lwEnd;
			return lastWord;
		}
		if (seekPos < prevWords.size()) return prevWords.get(seekPos++);

		flushBefore(prevIndex);

		prevLN = LN;
		prevIndex = index;

		Word w = readWord();

		if (seek == 1) {
			prevWords.add(w.copy());
			seekPos++;
		} else {
			seekPos = 0;
			prevWords.clear();

			lastWord = w;
			lwType = w.type();
			lwBegin = w.pos();
			lwEnd = index;
			lwStr = w.val();
		}

		afterWord();
		return w;
	}

	protected final void flushBefore(int i) {
		if ((aFlag&_AF_ROD) != 0) ((TextReader)input).releaseBefore(i);
	}

	public final void retractWord() {
		block: {
			if (seek <= 1) {
				if (seekPos > 0) {
					seekPos--;
					break block;
				}

				if (seek == 0) {
					seek = 2;
					if (lastWord == wd) lastWord.init(lwType, lwBegin, lwStr);
					break block;
				}
			}
			throw new IllegalArgumentException("Unable retract");
		}

		LN = prevLN;
		LNIndex = prevIndex;
	}
	// endregion
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
	public static boolean haveSlashes(CharSequence key, int since) {
		for (int i = since; i < key.length(); i++) {
			char c = key.charAt(i);
			int v = ADDSLASHES.getOrDefaultInt(c, 0);
			if (v != 0) return true;
		}
		return false;
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
	protected static int _removeSlash(CharSequence in, char c, Appendable out, int i) throws ParseException {
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
					try {
						int UIndex = (int) parseNumber(in, i, i += 8, _NF_HEX);
						if (Character.charCount(UIndex) > 1) out.append(Character.highSurrogate(UIndex)).append(Character.lowSurrogate(UIndex));
						else out.append((char) UIndex);
					} catch (Exception e) {
						throw new ParseException(in, "无效的\\UXXXXXXXX转义:"+e.getMessage(), i);
					}
				break;
				case -3: // uXXXX
					try {
						out.append((char) parseNumber(in, i, i += 4, _NF_HEX));
					} catch (Exception e) {
						throw new ParseException(in, "无效的\\uXXXX转义:"+e.getMessage(), i);
					}
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
	// region lexer
	public final boolean hasNext() { return index < input.length(); }
	protected final int lookAhead(int i) { return index+i >= input.length() ? -1 : input.charAt(index+i); }

	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (firstChar.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i+1))) {
						prevIndex = index = i;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default:
					prevIndex = index = i;
					return readSymbol();
				case C_NUMBER:
					prevIndex = index = i;
					return readDigit(false);
				case C_STRING:
					prevIndex = i;
					index = i+1;
					return readStringLegacy(c);
				case C_WHITESPACE: i++;
			}
		}

		index = i;
		return eof();
	}

	// region symbol matcher
	private final MutableInt posHold = new MutableInt();
	private final BiFunction<MutableInt, Word, Boolean> searcher = (kPos, v) -> {
		// staticy is not valid
		// 'static;' , 'static ' or '++;' are valid=
		if (isValidToken(kPos.getValue(), v)) {
			_bestLength = kPos.getValue();
			_bestMatch = v;
		}
		return false;
	};
	private int _bestLength;
	private Word _bestMatch;

	protected final Word readSymbol() throws ParseException {
		Word w = tryMatchToken();
		if (w != null) return w;
		return readLiteral();
	}

	protected final Word tryMatchToken() throws ParseException {
		CharSequence in = input;
		int i = index;

		_bestMatch = null;
		if (tokens != null)
			tokens.longestWithCallback(in, i, in.length(), posHold, searcher);

		Word w = _bestMatch;
		if (w == null) return null;

		if (w.getClass() == Word.class) {
			if (w.pos < 0) {
				index = i+_bestLength;
				return onSpecialToken0(w);
			}

			else w = formClip(w.type(), w.val());
		} else {
			w = w.copy();
			w.pos = index;
		}

		index = i+_bestLength;
		return w;
	}
	// endregion

	protected static final int ST_SINGLE_LINE_COMMENT = -2, ST_MULTI_LINE_COMMENT = -3, ST_STRING = -4, ST_LITERAL_STRING = -5;
	private Word onSpecialToken0(Word w) throws ParseException {
		switch (w.pos) {
			default: return onSpecialToken(w);
			case ST_SINGLE_LINE_COMMENT: singleLineComment(comment); return readWord();
			case ST_MULTI_LINE_COMMENT: multiLineComment(comment, w.val); return readWord();
			case ST_STRING: case ST_LITERAL_STRING:
				String s = w.val;
				if (s.length() != 1) throw new UnsupportedOperationException("readSlashString not support len > 1 terminator");
				return formClip(STRING, readSlashString(s.charAt(0), w.pos == ST_STRING));
		}
	}
	protected Word onSpecialToken(Word w) throws ParseException { throw new UnsupportedOperationException("unexpected error"); }

	protected boolean isValidToken(int off, Word w) {
		if (w.pos < 0) return true;

		off += index;
		if (off >= input.length()) return true;

		boolean prevNoLit = literalEnd.contains(input.charAt(off-1));
		boolean curNoLit = literalEnd.contains(input.charAt(off));

		return (prevNoLit^curNoLit) | prevNoLit;
	}

	protected Word readLiteral() throws ParseException {
		CharSequence in = input;
		int i = index;
		int prevI = i;

		MyBitSet ex = literalEnd;
		while (i < in.length()) {
			int c = in.charAt(i);
			if (ex.contains(c)) break;
			i++;
		}

		if (prevI == i) {
			if (i >= in.length()) return eof();
			throw err("literalEnd() failed on '"+in.charAt(i)+"'");
		}
		index = i;

		found.clear();
		return formClip(LITERAL, found.append(in, prevI, i));
	}

	protected Word readStringLegacy(char key) throws ParseException { return formClip(STRING, readSlashString(key, true)); }

	protected final Word wd = new Word();
	protected Word formClip(short id, CharSequence s) { return wd.init(id, prevIndex, s.toString()); }
	protected final Word eof() { return wd.init(EOF, prevIndex, "/EOF"); }
	// endregion
	// region 数字解析
	protected static final int DIGIT_HBO = 1, DIGIT_DFL = 2;
	protected Word readDigit(boolean sign) throws ParseException { return digitReader(sign, DIGIT_HBO); }
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException {
		if (reason.endsWith(":")) reason += input.charAt(i);
		throw err(reason, i);
	}
	protected void onNumberFlow(CharSequence str, short from, short to) throws ParseException {}

	private static final MyBitSet
		BIN_NUMBERS = MyBitSet.from("01Ll"),
		OCT_NUMBERS = MyBitSet.from("01234567Ll"),
		HEX_NUMBERS = MyBitSet.from("0123456789ABCDEFabcdefLl.Pp"),
		DEC_NUMBERS = MyBitSet.from("0123456789DEFdefLl."),
		REAL_NUMBERS = MyBitSet.from("0123456789DdEeFf"),
		REAL_NUMBERS_HEX = MyBitSet.from("0123456789ABCDEFabcdefPp"),
		REAL_NUMBERS_AFTER_EXP = MyBitSet.from("0123456789DdFf+-");
	private static final int _NF_HEX = 1, _NF_BIN = 2, _NF_OCT = 3, _NF_END = 4, _NF_UNDERSCORE = 8;

	/**
	 * 6 种数字，尽在掌握
	 */
	@SuppressWarnings("fallthrough")
	protected final Word digitReader(boolean sign, int oFlag) throws ParseException {
		CharSequence in = input;
		int i = index;

		double dd = 0xbeef.CP-3;
		boolean neg = sign && in.charAt(i++) == '-';

		/*
		 *  0   1     2     3
		 * int,long,float,double
		 */
		int type = 0;
		/*
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
			if (i == in.length()) return onInvalidNumber(oFlag, index, "lexer.number.eof");
			if ((c=in.charAt(i)) == '_' || !set.contains(c)) return onInvalidNumber(oFlag, i, "lexer.number.notNumber:");
		} else if (c == '.') { // .5之类的
			// 检测 .e .d .f
			if (!NUMBER.contains(in.charAt(i))) return onInvalidNumber(oFlag, i, "lexer.number.exceptDec");
			type = 3; set = REAL_NUMBERS;
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
				return onInvalidNumber(oFlag, i, set.contains(c)?"lexer.number.exceptBlank":"lexer.number.notNumber:");
			}

			switch (c) {
				case 'P': case 'p':
					assert ((flag&3) == _NF_HEX);
					type = 3;
					set = REAL_NUMBERS_AFTER_EXP;
				break;
				case 'E': case 'e': // 5e3之类的
					if ((flag&3) == _NF_HEX) break;
					type = 3;
					set = REAL_NUMBERS_AFTER_EXP;
				break;
				case '.':
					type = 3;
					set = (flag&3) == _NF_HEX ? REAL_NUMBERS_HEX : REAL_NUMBERS;
				break;
				case 'D': case 'd':
					if ((flag&3) == _NF_HEX && set != REAL_NUMBERS_AFTER_EXP) break;
					type = 3;
					flag |= _NF_END;
				break;
				case 'F': case 'f':
					if ((flag&3) == _NF_HEX && set != REAL_NUMBERS_AFTER_EXP) break;
					type = 2;
					flag |= _NF_END;
				break;
				case 'L': case 'l':
					type = 1;
					flag |= _NF_END;
				break;
				case '_':
					flag |= _NF_UNDERSCORE;
				break;
				case '+': case '-': // 必须跟在e后面
					c = in.charAt(i-1);
					if (c != 'E' && c != 'e' && c != 'p' && c != 'P') return onInvalidNumber(oFlag, i, "lexer.number.notNumber:");
				break;
			}
			if (++i == in.length()) break;
		}

		if ((flag & _NF_END) != 0) {
			if ((oFlag & DIGIT_DFL) == 0) return onInvalidNumber(oFlag, i, "设置DIGIT_DFL位以启用字母结尾的数字");
			i--;
		}
		assert i > prevI;

		// 结尾
		c = in.charAt(i-1);

		CharList v = found; v.clear();
		if ((flag & 3) == _NF_HEX && type > 1) {
			if (set != REAL_NUMBERS_AFTER_EXP) return onInvalidNumber(oFlag, i, "lexer.number.formatError");
			v.append("0x");
		}

		// 存在下划线
		if ((flag & _NF_UNDERSCORE) != 0) {
			if (c == '_') return onInvalidNumber(oFlag, i-1, "lexer.number.notNumber:");
			while (prevI < i) {
				c = in.charAt(prevI++);
				if (c != '_') v.append(c);
			}
		} else {
			v.append(in, prevI, i);
		}

		// 实数
		if (type > 1) {
			switch (c) {
				case 'P': case 'p':
				case 'E': case 'e':
				case '+': case '-':
					return onInvalidNumber(oFlag, i, "lexer.number.noExponent");
			}
		}

		if ((flag & _NF_END) != 0) i++;

		try {
			String represent = input.subSequence(index, i).toString();

			Word w;
			switch (type) {
				default:
				case 0:
					if ((flag &= 3) == 0 && !TextUtil.checkMax(TextUtil.INT_MAXS, v, 0, neg)) {
						if (TextUtil.checkMax(TextUtil.LONG_MAXS, v, 0, neg)) {
							onNumberFlow(v, INTEGER, LONG);
							w = new L(index, parseNumber(v, 4, neg), represent);
						} else {
							onNumberFlow(v, INTEGER, DOUBLE);
							w = new D(index, Double.parseDouble(v.toString()), represent);
						}
					} else {
						w = new I(index, (int) parseNumber(v, flag, neg), represent);
					}
				break;
				case 1: w = new L(index, parseNumber(v, (flag&3)|4, neg), represent); break;
				case 2:
					float fv = Float.parseFloat(v.toString());
					if (fv == Float.POSITIVE_INFINITY || fv == Float.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, index, "lexer.number.floatLarge");
					if (fv == 0 && !isZero(v)) return onInvalidNumber(oFlag, index, "lexer.number.floatSmall");
					w = new F(index, fv, represent);
				break;
				case 3:
					double dv = Double.parseDouble(v.toString());
					if (dv == Double.POSITIVE_INFINITY || dv == Double.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, index, "lexer.number.floatLarge");
					if (dv == 0 && !isZero(v)) return onInvalidNumber(oFlag, index, "lexer.number.floatSmall");
					w = new D(index, dv, represent);
				break;
			}
			index = i;
			return w;
		} catch (NumberFormatException e) {
			return onInvalidNumber(' ', i, e.getMessage());
		}
	}

	private static boolean isZero(CharList sb) {
		if (sb.startsWith("0x")) {
			for (int i = 2; i < sb.length(); i++) {
				char c = sb.list[i];
				if (c == 'p' || c == 'P') return true;
				if (c != '0' && c != '.') return false;
			}
		} else {
			for (int i = 0; i < sb.length(); i++) {
				char c = sb.list[i];
				if (c == 'e' || c == 'E') return true;
				if (c != '0' && c != '.') return false;
			}
		}

		return true;
	}
	private static final byte[][] RADIX_MAX = new byte[8][];
	private static final byte[] RADIX = {10, 16, 2, 8};
	static {
		// n mean see digitReader()
		// RM[n] => int max
		// RM[n|LONG] => long max

		RADIX_MAX[0] = TextUtil.INT_MAXS;
		RADIX_MAX[4] = TextUtil.LONG_MAXS;

		RADIX_MAX[_NF_HEX] = new byte[8];
		RADIX_MAX[_NF_HEX|4] = new byte[16];
		fill(_NF_HEX, 'f');

		RADIX_MAX[_NF_OCT] = new byte[16];
		RADIX_MAX[_NF_OCT|4] = new byte[32];
		fill(_NF_OCT, '7');

		RADIX_MAX[_NF_BIN] = new byte[32];
		RADIX_MAX[_NF_BIN|4] = new byte[64];
		fill(_NF_BIN, '1');
	}
	private static void fill(int off, char num) {
		fill(RADIX_MAX[off], num);
		fill(RADIX_MAX[off|4], num);
	}
	private static void fill(byte[] a0, char num) {
		int i = a0.length-1;
		// +1是因为他们是(be treated as)unsigned的
		a0[i] = (byte) (num+1);
		while (--i >= 0) {
			a0[i] = (byte) num;
		}
	}

	public static long parseNumber(CharSequence s, int i, int len, int mode) { return parseNumber(s, i, len, mode, false); }
	public static long parseNumber(CharSequence s, @Range(from = 0, to = 7) int mode, boolean neg) { return parseNumber(s, 0, s.length(), mode, neg); }
	public static long parseNumber(CharSequence s, int i, int len, @Range(from = 0, to = 7) int radixId, boolean neg) throws NumberFormatException {
		// range check done
		if (!TextUtil.checkMax(RADIX_MAX[radixId], s, 0, neg))
			throw new NumberFormatException("lexer.number.overflow:"+s);

		radixId = RADIX[radixId&3];
		long v = 0;
		while (i < len) {
			int digit = Character.digit(s.charAt(i), radixId);
			if (digit < 0) throw new NumberFormatException("s["+i+"]='"+s.charAt(i)+"' 不是合法的数字: "+s);
			i++;

			v *= radixId;
			v += digit;
		}

		return neg ? -v : v;
	}

	public final Word ISO8601Datetime(boolean must) throws ParseException {
		final int i = index;
		CharSequence in = input;
		AtomicReference<String> error = new AtomicReference<>();

		try {
			char c;

			int y = dateNum(4,0, error);
			dateDelim('-', error);

			int m = dateNum(2,12, error);
			dateDelim('-', error);

			int d = dateNum(2,31, error);

			long ts = (ACalendar.daySinceAD(y, m, d, null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000L;

			c = index == in.length() ? 0 : in.charAt(index);
			if (c != 'T' && c != 't' && c != ' ')
				return new L(RFCDATE_DATE, i, ts, in.subSequence(i, index).toString());
			index++;

			y = dateNum(2, 23, error);
			dateDelim(':', error);

			m = dateNum(2, 59, error);
			dateDelim(':', error);

			d = dateNum(2, 59, error);
			c = index == in.length() ? 0 : in.charAt(index++);

			ts += y * 3600000L + m * 60000L + d * 1000L;

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
				return new L(type, i, ts, in.subSequence(i, index).toString());
			}

			y = c == '+' ? -60000 : 60000;

			m = dateNum(2, 23, error);
			dateDelim(':', error);

			d = dateNum(2, 59, error);

			ts += (m * 60L + d) * y;
			return new L(RFCDATE_DATETIME_TZ, i, ts, in.subSequence(i, index).toString());
		} catch (OperationDone e) {
			if (must) throw err(error.get(), index);

			index = i;
			return null;
		}
	}
	private void dateDelim(char c, AtomicReference<String> err) {
		int i = index;
		if (input.charAt(i++) == c) index = i;
		else {
			err.set("错误的分隔符,期待"+c);
			throw OperationDone.INSTANCE;
		}
	}
	private int dateNum(int maxLen, int max, AtomicReference<String> err) {
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

		int num = (int) parseNumber(in, prevI, i, 0);
		if (max > 0 && num > max) {
			err.set("错误的时间范围");
			throw OperationDone.INSTANCE;
		}

		return num;
	}

	// endregion
	// region 注释解析
	protected final void singleLineComment(CharList row) {
		CharSequence in = input;

		int i = TextUtil.gNextCRLF(in, index);
		if (i < 0) i = in.length();

		if (row != null) row.append(in, index, i).trimLast().append('\n');
		index = i;
	}
	protected final void multiLineComment(CharList row, String end) throws ParseException {
		CharSequence in = input;
		int prevI = index;

		int i = TextUtil.gIndexOf(in, end, prevI, in.length());
		if (i < 0) throw err("在注释结束前遇到了文件尾");

		if (row != null) row.append(in, prevI, i-1).append('\n');
		index = i+end.length();
	}
	// endregion
	// region 行号
	private int LNIndex;
	protected int prevLN, LN;

	protected void afterWord() {
		if (LN == 0) return;

		int line = LN;

		CharSequence in = input;
		int i = LNIndex;
		while (i < index) {
			if (in.charAt(i++) == '\n') line++;
		}

		LNIndex = index;
		LN = line;
	}
	// endregion
	// region exception
	protected String i18n(String msg) { return msg; }

	public final Word except(int type, String v) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		throw err("未预料的: "+w.val()+", 期待: "+v);
	}
	public final void unexpected(String got, String expect) throws ParseException { throw err("未预料的: "+got+", 期待: "+expect); }
	public final void unexpected(String val) throws ParseException { throw err("未预料的'"+val+"'"); }

	public final ParseException err(String reason, int index) { return new ParseException(input, i18n(reason), index, null); }
	@Deprecated
	public ParseException err(String reason, Word word) { return new ParseException(input, i18n(reason) + "at" + word.val(), word.pos(), null); }

	public final ParseException err(String reason) { return err(reason, (Throwable) null); }
	public ParseException err(String reason, Throwable cause) { return new ParseException(input, i18n(reason), this.index - 1, cause); }
	// endregion
	@Override
	public String toString() { return "Lexer{"+"pos="+index + ", str="+input+"}"; }
}