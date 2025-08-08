package roj.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;
import roj.collect.BitSet;
import roj.collect.Int2IntMap;
import roj.collect.ArrayList;
import roj.collect.TrieTree;
import roj.util.OperationDone;
import roj.config.data.CInt;
import roj.util.FastFailException;
import roj.io.IOUtil;
import roj.text.*;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

import static roj.config.Token.*;

/**
 * @author Roj234
 * @since 2020/10/31 14:22
 */
public class Tokenizer {
	public static final BitSet
		NUMBER = BitSet.from("0123456789"),
		WHITESPACE = BitSet.from(" \r\n\t\f"); // #12288 Chinese space '　'

	private static final int _AF_ROD = 1;

	protected final CharList found = new CharList(32);
	protected BitSet literalEnd = WHITESPACE;

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

	public static Tokenizer arguments() {
		Tokenizer tokenizer = new Tokenizer();
		tokenizer.firstChar = NONE_C2C;
		return tokenizer;
	}
	public final List<Token> split(String seq) throws ParseException {
		init(seq);
		List<Token> list = new ArrayList<>();
		while (true) {
			Token w = next();
			if (w.type() == Token.EOF) break;
			list.add(w.copy());
		}
		return list;
	}
	public final List<String> splitToString(String seq) throws ParseException {
		init(seq);
		List<String> list = new java.util.ArrayList<>();
		while (true) {
			Token w = next();
			if (w.type() == Token.EOF) break;
			list.add(w.text());
		}
		return list;
	}

	protected TrieTree<Token> tokens;
	protected CharList comment;
	protected Int2IntMap firstChar = SIGNED_NUMBER_C2C;

	// region 构造
	public Tokenizer tokenIds(TrieTree<Token> i) { tokens = i; return this; }
	public Tokenizer literalEnd(BitSet i) { literalEnd = i; return this; }

	@Deprecated
	public static TrieTree<Token> generate(CharSequence text) {
		TrieTree<Token> indexes = new TrieTree<>();
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
			int val = (int) parseNumber(text, j, i, 0);

			indexes.put(key, new Token().init(val, 0, key));

			i++;
		}

		return indexes;
	}

	public static void addKeywords(TrieTree<Token> indexes, int begin, String... keywords) {
		for (String kw : keywords) {
			indexes.put(kw, new Token().init(begin++, 0, kw));
		}
	}
	public static void addSymbols(TrieTree<Token> indexes, BitSet noLiterals, int begin, String... symbols) {
		for (String kw : symbols) {
			indexes.put(kw, new Token().init(begin++, 0, kw));
			if (noLiterals != null) noLiterals.add(kw.charAt(0));
		}
	}
	public static void markSpecial(TrieTree<Token> indexes, String... tokens) {
		for (String kw : tokens) {
			indexes.get(kw).pos = -1;
		}
	}
	public static void addWhitespace(BitSet special) { special.addAll(" \t\r\n\f"); }
	// endregion

	public Tokenizer init(CharSequence seq) {
		seek = 0;
		seekPos = 0;
		prevTokens.clear();
		prevLN = LN = LNIndex = 0;
		prevIndex = index = 0;
		input = seq;
		aFlag = (byte) (seq instanceof TextReader ? _AF_ROD : 0);
		return this;
	}

	public final CharSequence getText() { return input; }

	// region mark, next, retract and reset
	private Token lastToken;
	private short lwType;
	private int lwBegin, lwEnd;
	private CharSequence lwStr;

	private byte seek;
	private short prevSeekPos, seekPos;
	private final ArrayList<Token> prevTokens = new ArrayList<>();
	public final void mark() throws ParseException {
		if ((seek&1) != 0) throw new UnsupportedOperationException("嵌套的seek");

		if (seek == 2) prevTokens.add(seekPos, lastToken.copy());
		seek = 1;
		prevSeekPos = seekPos;
	}
	public final void skip() { skip(0); }
	public final void skip(int offset) {
		assert (seek&1) != 0;
		seek = 0;
		int i = seekPos+offset;
		if (i == prevTokens.size()) {
			lastToken = prevTokens.getLast();
			lwEnd = index;
		}
		prevTokens.removeRange(0, i);
		seekPos = 0;
	}
	public final void retract() {
		assert (seek&1) != 0;
		seek = 0;
		seekPos = prevSeekPos;
		if (lastToken == wd) {
			lastToken.init(lwType, lwBegin, lwStr);
			lwEnd = index;
		}
	}

	public final Token next() throws ParseException {
		if (seek == 2) {
			seek = 0;
			index = lwEnd;
			return lastToken;
		}
		if (seekPos < prevTokens.size()) return prevTokens.get(seekPos++);

		// 20240608 change from prevIndex
		flushBefore(index);

		prevLN = LN;
		prevIndex = index;

		Token w = readWord();

		if (seek == 1) {
			prevTokens.add(w.copy());
			seekPos++;
		} else {
			seekPos = 0;
			prevTokens.clear();

			lastToken = w;
			lwType = w.type;
			lwBegin = w.pos;
			lwEnd = index;
			lwStr = w.text;
		}

		afterWord();
		return w;
	}

	protected final void flushBefore(int i) {
		if ((aFlag&_AF_ROD) != 0) ((TextReader)input).releaseBefore(i);
	}

	public final void retractWord() {
		if (seek <= 1) {
			if (seekPos > 0) {
				seekPos--;
				return;
			}

			if (seek == 0) {
				seek = 2;
				if (lastToken == wd) lastToken.init(lwType, lwBegin, lwStr);
				return;
			}
		}
		throw new IllegalArgumentException("Unable retract");
	}
	// endregion
	// region 转义
	private static final boolean USE_OCTAL_ESCAPE = true;
	private static final Int2IntMap ESCAPE_MAPPING = new Int2IntMap(), UNESCAPE_MAPPING = new Int2IntMap();
	private static void escape(char original, char escaped) {
		ESCAPE_MAPPING.putInt(original, escaped);
		UNESCAPE_MAPPING.putInt(escaped, original);
	}
	static {
		// 双向
		escape('\\', '\\');
		escape('\n', 'n');
		escape('\r', 'r');
		escape('\t', 't');
		escape('\b', 'b');
		escape('\f', 'f');
		escape('\'', '\'');
		escape('"', '"');

		// \s => ' '
		UNESCAPE_MAPPING.putInt('s', ' ');
		// \/ => '/'
		UNESCAPE_MAPPING.putInt('/', '/');

		// \377 (oct)
		for (int i = 0; i <= 7; i++) UNESCAPE_MAPPING.putInt('0'+i, -1);

		// \\uXXXX
		UNESCAPE_MAPPING.putInt('U', -2);
		UNESCAPE_MAPPING.putInt('u', -3);

		// 真正的换行符，未转义的那种
		UNESCAPE_MAPPING.putInt('\r', -4);
		UNESCAPE_MAPPING.putInt('\n', -5);
	}

	/**
	 * 对字符串进行转义处理。
	 */
	public static String escape(CharSequence string) { return escape(new CharList(), string).toStringAndFree(); }
	/**
	 * 将字符串转义结果追加到输出缓冲区。
	 * @return 输出缓冲区（链式调用）
	 */
	public static <T extends Appendable> T escape(T output, CharSequence string) { return escape(output, string, 0, '\0'); }
	/**
	 * 将字符串转义结果追加到输出缓冲区（带自定义排除字符）。
	 * @param startIndex 起始处理位置, 从 [startIndex, string.length()]
	 * @param charNoEscape 不转义的特定字符, 例如单双引号
	 * @return 输出缓冲区（链式调用）
	 */
	public static <T extends Appendable> T escape(T output, CharSequence string, int startIndex, char charNoEscape) {
		try {
			int prevI = startIndex;
			for (int i = startIndex; i < string.length(); i++) {
				char c = string.charAt(i);
				int escaped = ESCAPE_MAPPING.getOrDefaultInt(c, 0);
				if (escaped > 0 && c != charNoEscape) {
					output.append(string, prevI, i).append('\\').append((char) escaped);
					prevI = i+1;
				} else if (c < 32 || (c >= 127 && c <= 159)) { // 不能出现在字符串中的控制字符
					output.append(string, prevI, i);
					// 为了节约空间，在允许的情况下优先使用八进制转义
					if (USE_OCTAL_ESCAPE && (i+1 >= string.length() || !NUMBER.contains(string.charAt(i+1)))) {
						output.append('\\').append(Integer.toOctalString(c));
					} else {
						output.append("\\u");
						String hex = Integer.toHexString(c);
						int pad = hex.length();
						while (pad++ < 4) output.append('0');
						output.append(hex);
					}
					prevI = i+1;
				}
			}

			output.append(string, prevI, string.length());
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return output;
	}
	/**
	 * 检查字符串是否需要转义。
	 * @param string 输入字符串
	 * @param startIndex 起始检查位置
	 * @return 若存在需要转义的字符则返回true
	 */
	public static boolean needEscape(CharSequence string, int startIndex) {
		for (int i = startIndex; i < string.length(); i++) {
			char c = string.charAt(i);
			int v = ESCAPE_MAPPING.getOrDefaultInt(c, 0);
			if (v != 0) return true;
		}
		return false;
	}

	public static String unescape(CharSequence string) throws ParseException {return unescape(new CharList(), string).toStringAndFree();}
	public static <T extends Appendable> T unescape(T output, CharSequence string) throws ParseException {
		int i = 0;
		boolean slash = false;

		try {
			while (i < string.length()) {
				char c = string.charAt(i++);
				if (slash) {
					i = unescapeChar(string, c, output, i);
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

		if (slash) throw new ParseException(string, "未终止的 斜杠 (\\)", i, null);
		return output;
	}

	@SuppressWarnings("fallthrough")
	protected static int unescapeChar(CharSequence in, char c, Appendable out, int i) throws ParseException {
		try {
			int unescaped = UNESCAPE_MAPPING.getOrDefaultInt(c, 0);
			switch (unescaped) {
				// >0: 替换为
				default: {
					out.append((char) unescaped);
					return i;
				}
				// 0: 不支持
				case 0: {
					out.append('\\').append(in.charAt(i-1));
					return i;//throw new ParseException(in, "无效的转义 \\" + c, i-1, null);
				}
				case -1: // oct (000 - 377)
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
				case -2: // UXXXXXXXX
					try {
						int codepoint = (int) parseNumber(in, i, i += 8, _NF_HEX);
						if (codepoint > Character.MAX_CODE_POINT) throw new FastFailException("codePoint="+codepoint);

						if (Character.isBmpCodePoint(codepoint)) {
							out.append((char) codepoint);
						} else {
							out.append(Character.highSurrogate(codepoint))
							   .append(Character.lowSurrogate(codepoint));
						}
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
				case -5: out.append("\n"); while (WHITESPACE.contains(in.charAt(i))) i++;
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return i;
	}
	// endregion
	// region lexer
	@Deprecated public final boolean hasNext() { return index < input.length(); }
	protected final int lookAhead(int i) { return index+i >= input.length() ? -1 : input.charAt(index+i); }

	@SuppressWarnings("fallthrough")
	public Token readWord() throws ParseException {
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
					Token w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;continue;}
					return w;
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
	@Deprecated protected static final Token COMMENT_RETRY_HINT = new Token();
	@Deprecated private final CInt posHold = new CInt();
	@Deprecated private final BiFunction<CInt, Token, Boolean> searcher = (kPos, v) -> {
		// staticy is not valid
		// 'static;' , 'static ' or '++;' are valid=
		if (isValidToken(kPos.value, v)) {
			_bestLength = kPos.value;
			_bestMatch = v;
		}
		return false;
	};
	@Deprecated private int _bestLength;
	@Deprecated private Token _bestMatch;

	@Deprecated protected final Token readSymbol() throws ParseException {
		if (tokens != null) {
			Token w = tryMatchToken();
			if (w != null) return w;
		}
		return readLiteral();
	}

	@Deprecated protected final Token tryMatchToken() throws ParseException {
		CharSequence in = input;
		int i = index;

		Token w;
		_bestMatch = null;
		tokens.longestWithCallback(in, i, in.length(), posHold, searcher);
		if ((w = _bestMatch) == null) return null;

		if (w.getClass() == Token.class) {
			if (w.pos < 0) {
				index = i+_bestLength;
				w = onSpecialToken0(w);
				if (w != null) return w;
				return COMMENT_RETRY_HINT;
			}

			else w = formClip(w.type(), w.text());
		} else {
			w = w.copy();
			w.pos = index;
		}

		index = i+_bestLength;
		return w;
	}
	// endregion

	protected static final int ST_SINGLE_LINE_COMMENT = -2, ST_MULTI_LINE_COMMENT = -3, ST_STRING = -4, ST_LITERAL_STRING = -5;
	private Token onSpecialToken0(Token w) throws ParseException {
		switch (w.pos) {
			default: return onSpecialToken(w);
			case ST_SINGLE_LINE_COMMENT: singleLineComment(comment); return null;
			case ST_MULTI_LINE_COMMENT: multiLineComment(comment, w.text); return null;
			case ST_STRING, ST_LITERAL_STRING:
				CharSequence s = w.text;
				if (s.length() != 1) throw new UnsupportedOperationException("readSlashString not support len > 1 terminator");
				return formClip(STRING, readSlashString(s.charAt(0), w.pos == ST_STRING));
		}
	}
	protected Token onSpecialToken(Token w) throws ParseException { throw new UnsupportedOperationException("unexpected error"); }

	protected boolean isValidToken(int off, Token w) {
		if (w.pos < 0) return true;

		off += index;
		if (off >= input.length()) return true;

		boolean prevNoLit = literalEnd.contains(input.charAt(off-1));
		boolean curNoLit = literalEnd.contains(input.charAt(off));

		return (prevNoLit^curNoLit) | prevNoLit;
	}

	protected Token readLiteral() throws ParseException {
		CharSequence in = input;
		int i = index;
		int prevI = i;

		BitSet ex = literalEnd;
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
	@SuppressWarnings("fallthrough")
	protected final CharList readSlashString(char end, boolean unescape) throws ParseException {
		CharSequence in = input;
		int i = index;

		CharList v = found; v.clear();

		boolean slash = false;

		int prevI = i;
		while (i < in.length()) {
			char c = in.charAt(i++);
			if (slash) {
				if (unescape) {
					v.append(in, prevI, i-2);
					i = unescapeChar(input, c, v, i);
					prevI = i;
				}
				// 这样就不会处理c==end，相当于保留了\end唯一的转义符
				slash = false;
			} else if (c == end) {
				v.append(in, prevI, i-1);
				index = i;
				return v;
			} else if (c == '\\') {
				slash = true;
			}
		}

		throw err("在 转义字符串 终止前遇到了文件尾", index);
	}

	@Deprecated protected Token readStringLegacy(char key) throws ParseException { return formClip(STRING, readSlashString(key, true)); }

	protected Token newWord() {return new Token();}
	protected final Token wd = newWord();
	protected Token formClip(short id, CharSequence s) { return wd.init(id, prevIndex, s.toString()); }
	protected final Token eof() { return wd.init(EOF, prevIndex, "/EOF"); }
	// endregion
	// region 数字解析
	protected static final int DIGIT_HBO = 1, DIGIT_DFL = 2;
	protected Token readDigit(boolean sign) throws ParseException { return digitReader(sign, DIGIT_HBO); }
	protected Token onInvalidNumber(int flag, int i, String reason) throws ParseException {return readLiteral();}
	protected Token onNumberFlow(CharList str, short from, short to) throws ParseException {return null;}

	private static final BitSet
		BIN_NUMBERS = BitSet.from("01Ll"),
		OCT_NUMBERS = BitSet.from("01234567Ll."),
		HEX_NUMBERS = BitSet.from("0123456789ABCDEFabcdefLl.Pp"),
		DEC_NUMBERS = BitSet.from("0123456789DEFdefLl."),
		REAL_NUMBERS = BitSet.from("0123456789DdEeFf"),
		REAL_NUMBERS_HEX = BitSet.from("0123456789ABCDEFabcdefPp"),
		REAL_NUMBERS_AFTER_EXP = BitSet.from("0123456789DdFf+-");
	private static final int _NF_HEX = 1, _NF_BIN = 2, _NF_OCT = 3, _NF_END = 4, _NF_UNDERSCORE = 8;

	/**
	 * 6 种数字，尽在掌握
	 */
	@SuppressWarnings("fallthrough")
	protected final Token digitReader(boolean sign, int oFlag) throws ParseException {
		CharSequence in = input;
		int i = index;

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
		BitSet set = DEC_NUMBERS;
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

			if (c == '0' && prevI == i-1) prevI++;

			switch (c) {
				case 'P', 'p' -> {
					assert ((flag & 3) == _NF_HEX);
					type = 3;
					set = REAL_NUMBERS_AFTER_EXP;
				}
				case 'E', 'e' -> { // 5e3之类的
					if ((flag & 3) == _NF_HEX) break;
					type = 3;
					set = REAL_NUMBERS_AFTER_EXP;
				}
				case '.' -> {
					type = 3;
					set = (flag & 3) == _NF_HEX ? REAL_NUMBERS_HEX : REAL_NUMBERS;
				}
				case 'D', 'd' -> {
					if ((flag & 3) == _NF_HEX && set != REAL_NUMBERS_AFTER_EXP) break;
					type = 3;
					flag |= _NF_END;
				}
				case 'F', 'f' -> {
					if ((flag & 3) == _NF_HEX && set != REAL_NUMBERS_AFTER_EXP) break;
					type = 2;
					flag |= _NF_END;
				}
				case 'L', 'l' -> {
					type = 1;
					flag |= _NF_END;
				}
				case '_' -> flag |= _NF_UNDERSCORE;
				case '+', '-' -> { // 必须跟在e后面
					c = in.charAt(i - 1);
					if (c != 'E' && c != 'e' && c != 'p' && c != 'P')
						return onInvalidNumber(oFlag, i, "lexer.number.notNumber:");
				}
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
			CharSequence represent = input.subSequence(index, i);

			Token w;
			for(;;) {
			switch (type) {
				default -> {
					if ((flag &= 3) == 0 && !TextUtil.checkMax(TextUtil.INT_MAXS, v, 0, v.length(), neg)) {
						if (TextUtil.checkMax(TextUtil.LONG_MAXS, v, 0, v.length(), neg)) {
							w = onNumberFlow(v, INTEGER, LONG);
							if (w != null) break;
							w = Token.numberWord(index, _parseNumber(v, 4, neg), represent);
						} else {
							w = onNumberFlow(v, INTEGER, DOUBLE);
							if (w != null) break;
							type = 3;
							continue;
						}
					} else {
						if (!TextUtil.checkMax(RADIX_MAX[flag], v, 0, v.length(), neg)) {
							return onInvalidNumber(oFlag, index, "lexer.number.intLarge");
						}
						w = numberWord(index, (int) _parseNumber(v, flag, neg), represent);
					}
				}
				case 1 -> {
					if (!TextUtil.checkMax(RADIX_MAX[flag = (flag&3)|4], v, 0, v.length(), neg)) {
						return onInvalidNumber(oFlag, index, "lexer.number.longLarge");
					}
					w = Token.numberWord(index, _parseNumber(v, flag, neg), represent);
				}
				case 2 -> {
					float fv = FastDoubleParser.parseFloat(v);
					if (neg) fv = -fv;
					if (fv == Float.POSITIVE_INFINITY || fv == Float.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, index, "lexer.number.floatLarge");
					if (fv == 0 && !isZero(v)) return onInvalidNumber(oFlag, index, "lexer.number.floatSmall");
					w = Token.numberWord(index, fv, represent);
				}
				case 3 -> {
					double dv = FastDoubleParser.parseDouble(v);
					if (neg) dv = -dv;
					if (dv == Double.POSITIVE_INFINITY || dv == Double.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, index, "lexer.number.floatLarge");
					if (dv == 0 && !isZero(v)) return onInvalidNumber(oFlag, index, "lexer.number.floatSmall");
					w = Token.numberWord(index, dv, represent);
				}
			}
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
	private static long _parseNumber(CharList s, int radix, boolean neg) {
		radix = RADIX[radix&3];
		long v = 0;
		int len = s.length();
		for (int i = 0; i < len; i++) {
			v *= radix;
			v += Character.digit(s.charAt(i), radix);
		}
		return neg ? -v : v;
	}

	public static long parseNumber(CharSequence s, int i, int len, int mode) { return parseNumber(s, i, len, mode, false); }
	public static long parseNumber(CharSequence s, @Range(from = 0, to = 7) int mode, boolean neg) { return parseNumber(s, 0, s.length(), mode, neg); }
	public static long parseNumber(CharSequence s, int i, int len, @Range(from = 0, to = 7) int radixId, boolean neg) throws NumberFormatException {
		// range check done
		if (!TextUtil.checkMax(RADIX_MAX[radixId], s, i, len, neg))
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

	private static final ThreadLocal<String> IsoParserError = new ThreadLocal<>();
	@Contract("true -> !null")
	public final Token ISO8601Datetime(boolean must) throws ParseException {
		final int i = index;
		CharSequence in = input;

		try {
			char c;

			int y = dateNum(4,0);
			dateDelim('-');

			int m = dateNum(2,12);
			dateDelim('-');

			int d = dateNum(2,31);

			long ts = DateTime.daySinceUnixZero(y, m, d) * 86400000L;

			c = index == in.length() ? 0 : in.charAt(index);
			if (c != 'T' && c != 't' && c != ' ')
				return timeWord(RFCDATE_DATE, i, ts, in.subSequence(i, index).toString());
			index++;

			y = dateNum(2, 23);
			dateDelim(':');

			m = dateNum(2, 59);

			if (index < in.length()) {
				dateDelim(':');

				d = dateNum(2, 59);
			} else {
				d = 0;
			}
			c = index == in.length() ? 0 : in.charAt(index++);

			ts += y * 3600000L + m * 60000L + d * 1000L;

			if (c == '.') {
				ts += dateNum(3, 0);
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
				return timeWord(type, i, ts, in.subSequence(i, index).toString());
			}

			y = c == '+' ? -60000 : 60000;

			m = dateNum(2, 23);
			dateDelim(':');

			d = dateNum(2, 59);

			ts += (m * 60L + d) * y;
			return timeWord(RFCDATE_DATETIME_TZ, i, ts, in.subSequence(i, index).toString());
		} catch (OperationDone e) {
			var error = IsoParserError.get();
			IsoParserError.remove();

			if (must) throw err(error, index);

			index = i;
			return null;
		}
	}
	private void dateDelim(char c) {
		int i = index;
		if (input.charAt(i++) == c) index = i;
		else {
			IsoParserError.set("错误的分隔符,期待"+c);
			throw OperationDone.INSTANCE;
		}
	}
	private int dateNum(int maxLen, int max) {
		int i = index;
		CharSequence in = input;

		int prevI = i;
		while (i < in.length()) {
			if (!NUMBER.contains(in.charAt(i))) break;
			i++;
		}

		index = i;

		if (i-prevI <= 0 || i-prevI > maxLen) {
			IsoParserError.set("错误的时间范围");
			throw OperationDone.INSTANCE;
		}

		int num = (int) parseNumber(in, prevI, i, 0);
		if (max > 0 && num > max) {
			IsoParserError.set("错误的时间范围");
			throw OperationDone.INSTANCE;
		}

		return num;
	}

	// endregion
	// region 注释解析
	protected final void singleLineComment(CharList row) {
		CharSequence in = input;

		int i;
		if (row != null) {
			i = TextUtil.gAppendToNextCRLF(in, index, row);
			row.rtrim().append('\n');
		} else {
			i = TextUtil.gNextCRLF(in, index);
			if (i < 0) i = in.length();
		}
		index = i;
	}
	protected final void multiLineComment(CharList row, CharSequence end) throws ParseException {
		CharSequence in = input;
		int prevI = index;

		int i = TextUtil.indexOf(in, end, prevI, in.length());
		if (i < 0) throw err("在注释结束前遇到了文件尾");

		if (row != null) row.append(in, prevI, i-1).append('\n');
		index = i+end.length();
	}
	// endregion
	// region 行号
	public int LNIndex, LN;
	protected int prevLN;

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

	public final Token except(int type, String v) throws ParseException {
		Token w = next();
		if (w.type() == type) return w;
		throw err("未预料的: "+w.text()+", 期待: "+v);
	}
	public final void unexpected(String got, String expect) throws ParseException { throw err("未预料的: "+got+", 期待: "+expect); }
	public final void unexpected(String val) throws ParseException { throw err("未预料的'"+val+"'"); }

	public final ParseException err(String reason, int index) { return new ParseException(input, i18n(reason), index, null); }
	public final ParseException err(String reason) { return err(reason, null); }
	public ParseException err(String reason, Throwable cause) { return new ParseException(input, i18n(reason), prevIndex, cause); }
	// endregion
	@Override
	public String toString() { return "Tokenizer{"+"pos="+index+", str="+input+"}"; }
}