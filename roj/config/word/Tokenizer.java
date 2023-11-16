package roj.config.word;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.function.BiFunction;

/**
 * @author Roj234
 * @since 2022/9/3 22:40
 */
public class Tokenizer extends ITokenizer {
	public static final int PARSE_NUMBER = 1, PARSE_SIGNED_NUMBER = 2, JAVA_COMMENT = 4, COMPUTE_LINES = 8;

	protected static final int C_DEFAULT = 0, C_WHITESPACE = 1, C_COMMENT = 2, C_SYH = 3, C_DYH = 4, C_MAY__NUMBER_SIGN = 5, C_NUMBER = 6;
	protected static final Int2IntMap DEFAULT_C2C = new Int2IntMap();
	static { fcSetDefault(DEFAULT_C2C, PARSE_NUMBER|PARSE_SIGNED_NUMBER|JAVA_COMMENT); }

	protected static void fcSetDefault(Int2IntMap c, int flag) {
		fcFill(c, " \r\n\t\f", C_WHITESPACE);
		if ((flag & JAVA_COMMENT) != 0) c.putInt('/', C_COMMENT);
		c.putInt('"', C_SYH);
		c.putInt('\'', C_DYH);
		if ((flag & PARSE_SIGNED_NUMBER) != 0) fcFill(c, "+-", C_MAY__NUMBER_SIGN);
		if ((flag & PARSE_NUMBER) != 0) fcFill(c, "0123456789", C_NUMBER);
	}
	protected static void fcFill(Int2IntMap map, String k, int v) {
		for (int i = 0; i < k.length(); i++) {
			map.putInt(k.charAt(i), v);
		}
	}
	protected static void fcFillRange(Int2IntMap map, String k, int begin) {
		for (int i = 0; i < k.length(); i++) {
			map.putInt(k.charAt(i), begin++);
		}
	}
	public static Tokenizer arguments() { return new Tokenizer().literalEnd(" \r\n\t\f").defaultC2C(0); }

	protected I18n i18n = I18n.NULL;
	protected TrieTree<Word> tokens;
	protected CharList comment;
	protected Int2IntMap firstChar = DEFAULT_C2C;

	public Tokenizer tokenIds(TrieTree<Word> i) {
		tokens = i;
		return this;
	}
	public Tokenizer literalEnd(CharSequence s) {
		return literalEnd(MyBitSet.from(s));
	}
	public Tokenizer literalEnd(MyBitSet i) {
		literalEnd = i;
		return this;
	}
	public Tokenizer lang(I18n i) {
		i18n = i;
		return this;
	}
	public Tokenizer defaultC2C(int i) {
		aFlag = (byte) i;

		if (firstChar == DEFAULT_C2C) firstChar = new Int2IntMap();
		else firstChar.clear();
		fcSetDefault(firstChar, i);

		return this;
	}

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
			int val = TextUtil.parseInt(text, j, i, 10);

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
			noLiterals.add(kw.charAt(0));
		}
	}
	public static void markSpecial(TrieTree<Word> indexes, String... tokens) {
		for (String kw : tokens) {
			indexes.get(kw).index = -1;
		}
	}
	public static void addWhitespace(MyBitSet special) {
		special.addAll(" \t\r\n\f");
	}

	public Tokenizer init(CharSequence seq) {
		if ((aFlag & COMPUTE_LINES) != 0) {
			LN = 1;
			LNIndex = 0;
			if (lh != null) lh.handleLineNumber(1);
		}

		super.init(seq);
		return this;
	}

	protected int prevLN, LN, LNIndex;
	private LineHandler lh;

	public final void setLineHandler(LineHandler lh) {
		if (LN == 0) throw new IllegalStateException("Set COMPUTE_LINES flag before call init() method");
		this.lh = lh;
		if (lh != null) lh.handleLineNumber(LN);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		prevLN = LN;

		CharSequence in = input;
		int i = index;

		try {
			while (i < in.length()) {
				char c = in.charAt(i++);
				switch (firstChar.getOrDefaultInt(c, 0)) {
					case C_WHITESPACE: break;
					case C_MAY__NUMBER_SIGN:
						if (i < in.length() && NUMBER.contains(in.charAt(i))) {
							prevIndex = index = i-1;
							return readDigit(true);
						}
						// fall to literal(symbol)
					default:
					case C_DEFAULT: prevIndex = index = i-1; return readSymbol();
					case C_NUMBER: prevIndex = index = i-1; return readDigit(false);
					case C_COMMENT:
						prevIndex = index = i;
						Word w = javaComment(comment);
						if (w != null) return w;
						i = index;
						break;
					case C_DYH: prevIndex = index = i-1; return readConstChar();
					case C_SYH: prevIndex = index = i-1; return readConstString(c);
				}
			}
		} finally {
			checkLine();
		}
		index = i;
		return eof();
	}

	@Override
	public final void retractWord() {
		int w = prevIndex;
		super.retractWord();
		//LN = prevLN;
		//LNIndex = w;
	}
	@SuppressWarnings("fallthrough")
	protected final void checkLine() {
		if (LN == 0) return;

		int line = LN;

		CharSequence in = input;
		int i = LNIndex;
		loop:
		while (i < index) {
			switch (in.charAt(i++)) {
				case '\r':
					// since only LF can be next char (when line advanced)
					if (i == index) break loop;
					if (in.charAt(i) != '\n') break;
					else i++;
				case '\n': line++;
			}
		}
		LNIndex = index;

		if (line != LN) {
			LN = line;
			if (lh != null) lh.handleLineNumber(line);
		}
	}

	private final MutableInt posHold = new MutableInt();
	private final BiFunction<MutableInt, Word, Boolean> searcher = (kPos, v) -> {
		// staticy is not valid
		// 'static;' , 'static ' or '++;' are valid=
		if (isValidCombo(kPos.getValue(), v)) {
			_bestLength = kPos.getValue();
			_bestMatch = v;
		}
		return false;
	};
	private int _bestLength;
	private Word _bestMatch;
	@Override
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
			if (w.index < 0) {
				index = i+_bestLength;
				return _formClip(w);
			}

			else w = formClip(w.type(), w.val());
		} else {
			w = w.copy();
			w.index = index;
		}

		index = i+_bestLength;
		return w;
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
			throw err("literalEnd() failed on '" + in.charAt(i)+"'");
		}
		index = i;

		found.clear();
		return formLiteralClip(found.append(in, prevI, i));
	}

	protected Word _formClip(Word w) throws ParseException {
		throw new UnsupportedOperationException("Not implemented");
	}

	protected boolean isValidCombo(int off, Word word) {
		off += index;
		if (off >= input.length()) return true;

		boolean prevNoLit = literalEnd.contains(input.charAt(off-1));
		boolean curNoLit = literalEnd.contains(input.charAt(off));

		return (prevNoLit^curNoLit) | prevNoLit;
	}

	public ParseException err(String reason, Word word) {
		return new ParseException(input, i18n.translate(reason) + i18n.at + word.val(), word.pos(), null);
	}
	public ParseException err(String reason, Throwable cause) {
		return new ParseException(input, i18n.translate(reason), this.index - 1, cause);
	}
}
