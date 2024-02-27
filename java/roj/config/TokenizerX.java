package roj.config;

import org.jetbrains.annotations.Range;
import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static roj.config.Word.*;

/**
 * @author Roj234
 * @since 2024/5/11 10:53
 */
public class TokenizerX {
	public static final MyBitSet
		NUMBER = MyBitSet.from("0123456789"),
		WHITESPACE = MyBitSet.from(" \r\n\t\f"); // #12288 Chinese space '　'

	protected final CharList found = new CharList();

	// region Reader
	private char[] buf = new char[256];
	private int off, len;
	protected int readPos;
	protected final int getc(int x) {
		if (x > readPos) readPos = x;
		if (off+x >= len && !feed(len - off - x)) return -1;
		return buf[off+x];
	}
	protected void releasec() {
		if (LN != 0) {
			int line = LN;
			for (int i = 0; i <= readPos; i++) {
				if (buf[off+i] == '\n') line++;
			}
			LN = line;
		}

		off += readPos+1;
		_index += readPos+1;
		readPos = -1;
	}
	protected final CharList found(int off, int end) {return found.append(buf, this.off+off, this.off+end);}
	private boolean feed(int extraBytes) {
		int keepSize = len - off;
		if (off > 0) System.arraycopy(buf, off, buf, 0, keepSize);
		off = 0;

		int readable = buf.length - keepSize;
		if (readable < extraBytes) {
			int newSize = MathUtils.getMin2PowerOf(keepSize + extraBytes);
			char[] newBuf = new char[newSize];
			System.arraycopy(buf, 0, newBuf, 0, keepSize);
			ArrayCache.putArray(buf);
			buf = newBuf;
			readable = newSize - keepSize;
		}

		len = keepSize+readable;
		while (readable > 0) {
			int r = 0;
			try {
				r = _in.read(buf, keepSize, readable);
			} catch (IOException e) {
				Helpers.athrow(e);
			}

			if (r < 0) {
				len = -1;
				return false;
			}
			readable -= r;
		}
		return true;
	}
	// endregion

	public TokenizerX() {}

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

	public static TokenizerX arguments() { return new TokenizerX().literalEnd(WHITESPACE); }
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
	protected MyBitSet literalEnd = WHITESPACE;
	protected CharList comment;
	protected Int2IntMap firstChar = SIGNED_NUMBER_C2C;

	// region 构造
	public TokenizerX tokenIds(TrieTree<Word> i) { tokens = i; return this; }
	public TokenizerX literalEnd(MyBitSet i) { literalEnd = i; return this; }

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

	public TokenizerX init(CharSequence seq) {
		prevLN = LN = 0;
		_index = off = len = 0;
		readPos = -1;
		_in = seq instanceof Reader r ? r : new StringReader(seq.toString());
		return this;
	}

	public final CharSequence getText() { return _in.toString(); }

	// region mark, next, retract and reset
	private static final int RM_OK = 0, RM_RECORD = 1, RM_END = 2;

	private Word last;
	private short lwType;
	private int lwPos;
	private String lwVal;

	private byte seek;
	private short prevSeekPos, seekPos;
	private final SimpleList<Word> prevWords = new SimpleList<>();
	public final void mark() throws ParseException {
		if (seek == RM_RECORD) throw new UnsupportedOperationException("嵌套的seek");
		if (seek == RM_END) prevWords.add(last == wd ? last.copy() : last);
		seek = RM_RECORD;
		prevSeekPos = seekPos;
	}
	public final void reset() { seekPos = 0; }
	public final void skip() {
		assert seek == RM_RECORD;
		seek = RM_OK;

		int i = seekPos;
		if (i == prevWords.size())
			last = prevWords.getLast();
		prevWords.removeRange(0, i);

		seekPos = 0;
	}
	public final void retract() {
		assert seek == RM_RECORD;
		seek = RM_OK;

		seekPos = prevSeekPos;
		if (last == wd)
			last.init(lwType, lwPos, lwVal);
	}

	public final Word next() throws ParseException {
		if (seek == RM_END) {
			seek = RM_OK;
			return last;
		}

		if (seekPos < prevWords.size()) return prevWords.get(seekPos++);

		Word w = readWord();
		if (seek == RM_RECORD) {
			if (w == wd) w = w.copy();
			prevWords.add(w);
			seekPos++;
		} else {
			seekPos = 0;
			prevWords.clear();

			last = w;
			lwType = w.type();
			lwPos = w.pos();
			lwVal = w.val();
		}
		return w;
	}

	public final void retractWord() {
		block: {
			if (seek <= RM_RECORD) {
				if (seekPos > 0) {
					seekPos--;
					break block;
				}

				if (seek == RM_OK) {
					seek = RM_END;
					if (last == wd) last.init(lwType, lwPos, lwVal);
					break block;
				}
			}
			throw new IllegalArgumentException("Unable retract");
		}
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

	@SuppressWarnings("fallthrough")
	protected final CharList readSlashString(char end, boolean doDeSlash) throws ParseException {
		found.clear();
		boolean slash = false;

		releasec();
		int i = 0;

		int c;
		while ((c = getc(i)) >= 0) {
			if (slash) {
				if (doDeSlash) {
					found(0, i);
					releasec();

					i = deslashes(c, 0);
					releasec();
				}

				// 这样就不会处理c==end，相当于保留了\end唯一的转义符
				slash = false;
			} else if (c == end) {
				readPos++;
				return found(0, i);
			} else if (c == '\\') {
				slash = true;
			}

			i++;
		}

		throw error("在 转义字符串 终止前遇到了EOF");
	}
	@SuppressWarnings("fallthrough")
	private int deslashes(int c, int i) throws ParseException {
		CharList out = found;

		int v = DESLASHES.getOrDefaultInt(c, 0);

		switch (v) {
			default -> out.append((char) v);
			case 0 -> out.append('\\').append(c);
			//throw new ParseException(in, "无效的转义 \\" + c, i-1, null);

			case -1 -> out.append('/');
			case -2 -> { // UXXXXXXXX
				try {
					int UIndex = (int) parseNumber(i, 8, _NF_HEX);
					if (Character.charCount(UIndex) > 1) out.append(Character.highSurrogate(UIndex)).append(Character.lowSurrogate(UIndex));
					else out.append((char) UIndex);
				} catch (Exception e) {
					throw error("无效的\\UXXXXXXXX转义:"+e.getMessage());
				}
			}
			case -3 -> { // uXXXX
				try {
					out.append((char) parseNumber(i, 4, _NF_HEX));
				} catch (Exception e) {
					throw error("无效的\\uXXXX转义:"+e.getMessage());
				}
			}
			case -4 -> {
				out.append("\r");
				if (getc(++i) == '\n') i++;
			}
			case -5 -> {
				out.append("\n");
				while (WHITESPACE.contains(getc(++i)));
			}
			case -6 -> { // oct (000 - 377)
				int d = getc(++i);
				int xi = c - '0';
				if (d >= '0' && d <= '7') {
					xi = (xi << 3) + d - '0';

					int e = getc(++i);
					if (c <= '3' && e >= '0' && e <= '7') {
						i++;
						xi = (xi << 3) + e - '0';
					}
				}
				out.append((char) xi);
			}
		}

		return i;
	}
	// endregion
	// region lexer
	public final boolean hasNext() { return len >= 0; }

	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		int c;
		while (true) {
			releasec();

			if ((c = getc(0)) < 0) return eof();

			switch (firstChar.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (NUMBER.contains(getc(1))) return readDigit(true);
					// fall to literal(symbol)
				default:
					Word w = readSymbol();
					if (w != null) return w;
				break;
				case C_NUMBER: return readDigit(false);
				case C_STRING: return readStringLegacy((char) c);
				case C_WHITESPACE: break;
			}
		}
	}

	// region symbol matcher
	private static final Word SKIP = new Word();
	protected final Word readSymbol() throws ParseException {
		Word w = tryMatchToken();
		if (w != null) return w == SKIP ? null : w;
		return readLiteral();
	}
	@SuppressWarnings("unchecked")
	protected final Word tryMatchToken() throws ParseException {
		CharList tmp = found;
		TrieTree.Entry<Word> entry = tokens.getRoot();
		Word w = null;

		int i = 0;
		int realPos = readPos;

		loop:
		while (true) {
			int c = getc(i);
			if (c < 0) break;

			TrieTree.Entry<Word> next = (TrieTree.Entry<Word>) entry.getChild((char) c);
			if (next == null) break;

			if (next.length() > 1) {
				tmp.clear(); next.append(tmp);

				for (int j = 1; j < tmp.length(); j++) {
					if (getc(i+j) != tmp.charAt(j)) {
						break loop;
					}
				}
			}
			i += next.length();

			if (next.isLeaf() && isValidToken(i, next.getValue())) {
				realPos = i;
				w = next.getValue();
			}

			entry = next;
		}
		readPos = realPos;

		if (w == null) return null;

		if (w.getClass() == Word.class) {
			if (w.pos < 0) return onSpecialToken0(w);
			else w = word(w.type(), w.val());
		} else {
			w = w.copy();
			w.pos = _index + i;
		}

		return w;
	}

	protected boolean isValidToken(int off, Word w) {
		if (w.pos < 0) return true;
		if (getc(off) < 0) return true;

		boolean prevNoLit = literalEnd.contains(getc(off-1));
		boolean curNoLit = literalEnd.contains(getc(off));

		return (prevNoLit^curNoLit) | prevNoLit;
	}
	// endregion

	protected static final int ST_SINGLE_LINE_COMMENT = -2, ST_MULTI_LINE_COMMENT = -3, ST_STRING = -4, ST_LITERAL_STRING = -5;
	private Word onSpecialToken0(Word w) throws ParseException {
		switch (w.pos) {
			default: return onSpecialToken(w);
			case ST_SINGLE_LINE_COMMENT: tillNextCRLF(comment); return SKIP;
			case ST_MULTI_LINE_COMMENT: tillEndMarker(comment, w.val); return SKIP;
			case ST_STRING, ST_LITERAL_STRING:
				String s = w.val;
				if (s.length() != 1) throw new UnsupportedOperationException("readSlashString not support len > 1 terminator");
				return word(STRING, readSlashString(s.charAt(0), w.pos == ST_STRING));
		}
	}
	protected Word onSpecialToken(Word w) throws ParseException { throw new UnsupportedOperationException("unexpected error"); }

	protected Word readLiteral() throws ParseException {
		int c;
		int i = 0;
		MyBitSet ex = literalEnd;
		while ((c = getc(i)) >= 0) {
			if (ex.contains(c)) break;
			i++;
		}

		if (i == 0) throw error("literalEnd() failed on '"+(char)c+"'");

		found.clear();
		return word(LITERAL, found(0, i));
	}

	protected Word readStringLegacy(char key) throws ParseException { return word(STRING, readSlashString(key, true)); }

	protected final Word wd = new Word();
	protected Word word(short id, CharSequence s) { return wd.init(id, _index, s.toString()); }
	protected final Word eof() { return wd.init(EOF, _index, "/EOF"); }
	// endregion
	// region 数字解析
	protected static final int DIGIT_HBO = 1, DIGIT_DFL = 2;
	protected Word readDigit(boolean sign) throws ParseException { return digitReader(sign, DIGIT_HBO); }
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException {
		if (reason.endsWith(":")) reason += (char)getc(i);
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
		int i = 0;

		boolean neg = sign && getc(i++) == '-';

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
		int c = getc(i);
		check1:
		if (c == '0' && getc(i+1) >= 0 && (oFlag & DIGIT_HBO) != 0) {
			c = getc(++i);
			switch (c) {
				case 'X': case 'x': flag = _NF_HEX; set = HEX_NUMBERS; i++; break;
				case 'B': case 'b': flag = _NF_BIN; set = BIN_NUMBERS; i++; break;
				default:
					if (NUMBER.contains(c)) {flag = _NF_OCT; set = OCT_NUMBERS;}
					i--;
					break check1;
			}
			c = getc(i);
			// 检测 0x_ 0b_, 保留 0_ 和 00_
			if (c < 0) return onInvalidNumber(oFlag, i, "lexer.number.eof");
			if (c == '_' || !set.contains(c)) return onInvalidNumber(oFlag, i, "lexer.number.notNumber:");
		} else if (c == '.') { // .5之类的
			// 检测 .e .d .f
			if (!NUMBER.contains(getc(i))) return onInvalidNumber(oFlag, i, "lexer.number.exceptDec");
			type = 3; set = REAL_NUMBERS;
		}

		int prevI = i;
		while (true) {
			c = getc(i);
			if (c < 0) break;

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
					c = getc(i-1);
					if (c != 'E' && c != 'e' && c != 'p' && c != 'P') return onInvalidNumber(oFlag, i, "lexer.number.notNumber:");
					break;
			}
			i++;
		}

		if ((flag & _NF_END) != 0) {
			if ((oFlag & DIGIT_DFL) == 0) return onInvalidNumber(oFlag, i, "设置DIGIT_DFL位以启用字母结尾的数字");
			i--;
		}
		assert i > prevI;

		// 结尾
		c = getc(i-1);

		CharList v = found; v.clear();
		if ((flag & 3) == _NF_HEX && type > 1) {
			if (set != REAL_NUMBERS_AFTER_EXP) return onInvalidNumber(oFlag, i, "lexer.number.formatError");
			v.append("0x");
		}

		// 存在下划线
		if ((flag & _NF_UNDERSCORE) != 0) {
			if (c == '_') return onInvalidNumber(oFlag, i-1, "lexer.number.notNumber:");
			while (prevI < i) {
				c = getc(prevI++);
				if (c != '_') v.append(c);
			}
		} else {
			found(prevI, i);
		}

		// 实数
		if (type > 1) {
			switch (c) {
				case 'P', 'p':
				case 'E', 'e':
				case '+', '-':
					return onInvalidNumber(oFlag, i, "lexer.number.noExponent");
			}
		}

		if ((flag & _NF_END) != 0) i++;

		try {
			String represent = new String(buf, off, i);

			Word w;
			switch (type) {
				default:
				case 0:
					if ((flag &= 3) == 0 && !TextUtil.checkMax(TextUtil.INT_MAXS, v, 0, v.length(), neg)) {
						if (TextUtil.checkMax(TextUtil.LONG_MAXS, v, 0, v.length(), neg)) {
							onNumberFlow(v, INTEGER, LONG);
							w = Word.numberWord(_index, parseNumber(v, 4, neg), represent);
						} else {
							onNumberFlow(v, INTEGER, DOUBLE);
							w = Word.numberWord(_index, Double.parseDouble(v.toString()), represent);
						}
					} else {
						w = numberWord(_index, (int) parseNumber(v, flag, neg), represent);
					}
					break;
				case 1: w = Word.numberWord(_index, parseNumber(v, (flag&3)|4, neg), represent); break;
				case 2:
					float fv = Float.parseFloat(v.toString());
					if (fv == Float.POSITIVE_INFINITY || fv == Float.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, _index, "lexer.number.floatLarge");
					if (fv == 0 && !isZero(v)) return onInvalidNumber(oFlag, _index, "lexer.number.floatSmall");
					w = Word.numberWord(_index, fv, represent);
					break;
				case 3:
					double dv = Double.parseDouble(v.toString());
					if (dv == Double.POSITIVE_INFINITY || dv == Double.NEGATIVE_INFINITY) return onInvalidNumber(oFlag, _index, "lexer.number.floatLarge");
					if (dv == 0 && !isZero(v)) return onInvalidNumber(oFlag, _index, "lexer.number.floatSmall");
					w = Word.numberWord(_index, dv, represent);
					break;
			}
			_index = i;
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

	private long parseNumber(int i, int len, int mode) throws ParseException {
		CharList tmp = found; tmp.clear();
		while (len-- > 0) {
			int c = getc(i++);
			if (c < 0) throw err("未预料的EOF");

			tmp.append((char)c);
		}
		return parseNumber(tmp, mode, false);
	}
	private long parseNumber(CharList tmp, @Range(from = 0, to = 7) int radixId, boolean neg) {
		// range check done
		if (!TextUtil.checkMax(RADIX_MAX[radixId], tmp, 0, tmp.length(), neg))
			throw new NumberFormatException("lexer.number.overflow:"+tmp.toString());

		radixId = RADIX[radixId&3];
		int i = 0;
		long v = 0;
		while (i < tmp.length()) {
			int digit = Character.digit(tmp.charAt(i), radixId);
			if (digit < 0) throw new NumberFormatException(tmp.toString()+"(RestOf) 不是合法的数字");
			i++;

			v *= radixId;
			v += digit;
		}

		return neg ? -v : v;
	}

	// endregion
	// region 注释解析
	protected final void tillNextCRLF(CharList row) {
		int i = 0;
		while (true) {
			int c = getc(i);
			if (c < 0 || c == '\n') {
				if (row != null) row.append(buf, off, off+i).trimLast().append('\n');
				break;
			}

			i++;

			if (c == '\r' && getc(i) == '\n') {
				if (row != null) row.append(buf, off, off+i-1).trimLast().append('\n');
				break;
			}
		}
	}
	protected final void tillEndMarker(CharList row, String end) throws ParseException {
		int i = 0;
		while (true) {
			int c = getc(i);
			if (c < 0) throw err("在注释结束前遇到了文件尾");
			block:
			if (c == end.charAt(0)) {
				for (int j = 1; j < end.length(); j++) {
					c = getc(i + j);
					if (c != end.charAt(j)) break block;
				}
				readPos++;

				if (row != null) row.append(buf, off, off+i).trimLast().append('\n');
				break;
			}

			i++;
		}
	}
	// endregion
	// region 行号
	protected int prevLN, LN;
	// endregion

	private CharSequence _text;
	private Reader _in;
	protected int _index;
	private ParseException error(String reason) {
		return new ParseException(_in.toString(), reason, _index);
	}

	// region exception
	protected String i18n(String msg) { return msg; }

	public final Word except(int type, String v) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		throw err("未预料的: "+w.val()+", 期待: "+v);
	}
	public final void unexpected(String got, String expect) throws ParseException { throw err("未预料的: "+got+", 期待: "+expect); }
	public final void unexpected(String val) throws ParseException { throw err("未预料的'"+val+"'"); }

	public final ParseException err(String reason, int index) { return new ParseException(_in.toString(), i18n(reason), index, null); }
	@Deprecated
	public ParseException err(String reason, Word word) { return new ParseException(_in.toString(), i18n(reason) + "at" + word.val(), word.pos(), null); }

	public final ParseException err(String reason) { return err(reason, (Throwable) null); }
	public ParseException err(String reason, Throwable cause) { return new ParseException(_in.toString(), i18n(reason), _index - 1, cause); }
	// endregion
	@Override
	public String toString() { return "Lexer{"+"pos="+_index+", str="+_in.toString()+"}"; }
}