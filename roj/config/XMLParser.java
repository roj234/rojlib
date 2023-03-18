package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.function.Predicate;

import static roj.config.JSONParser.*;
import static roj.config.word.Word.*;

/**
 * XML - 可扩展标记语言（EXtensible Markup Language）
 *
 * @author Roj234
 */
public class XMLParser extends Parser<CMapping> {
	private static final short left_curly_bracket = 13, right_curly_bracket = 14, slash = 15,
		equ = 16, ask = 17, element_end = 18, header_start = 19,
		header_end = 20, CDATA_STRING = 21;

	private static final TrieTree<Word> XML_TOKENS = new TrieTree<>();
	private static final MyBitSet XML_LENDS = new MyBitSet();
	private static final Int2IntMap XML_FC = new Int2IntMap();
	static {
		addKeywords(XML_TOKENS, 10, "true", "false", "null");
		addSymbols(XML_TOKENS, XML_LENDS, 13, "<", ">", "/", "=", "?", "</", "<?", "?>");
		addWhitespace(XML_LENDS);

		fcSetDefault(XML_FC, 3);
		XML_FC.put('\'', C_SYH);
		XML_FC.put('<', 2); // magic number in switch
	}

	{ tokens = XML_TOKENS; literalEnd = XML_LENDS; firstChar = XML_FC; }

	public static final int FORCE_XML_HEADER = 1, LENIENT = 2, PROCESS_ENTITY = 16;

	private static final int _SKIP_CONST_STRING = 512;

	public static XHeader parses(CharSequence string) throws ParseException {
		return new XMLParser().parseTo1(string, LENIENT);
	}
	public static XHeader parses(CharSequence string, int flag) throws ParseException {
		return new XMLParser().parseTo1(string, flag);
	}

	@Override
	public CMapping parse(CharSequence text, int flag) throws ParseException {
		return parseTo1(text, flag).toJSON();
	}

	@Override
	public int availableFlags() {
		return NO_EOF | FORCE_XML_HEADER | LENIENT | UNESCAPED_SINGLE_QUOTE | PROCESS_ENTITY;
	}

	@Override
	public String format() {
		return "XML";
	}

	public XHeader parseTo1(CharSequence text, int flag) throws ParseException {
		this.flag = flag;
		init(text);

		XHeader x;
		try {
			x = xmlHeader(flag);
			if ((flag & NO_EOF) == 0) except(EOF);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
		return x;
	}

	public XHeader xmlHeader(int flag) throws ParseException {
		XHeader entry = new XHeader();

		Word w = next();
		if (w.type() != header_start) {
			if ((flag & FORCE_XML_HEADER) != 0) unexpected(w.val(), "<?xml");
			retractWord();
		} else {
			if (!readLiteral().val().equalsIgnoreCase("xml")) {
				throw err("<? 但不是 <?xml");
			}

			w = next();
			attributes:
			while (true) {
				switch (w.type()) {
					case header_end: break attributes;
					case LITERAL: w = readAttribute(entry, w); break;
					default: unexpected(w.val(), "属性 / '>'");
				}
			}
		}

		children:
		while (hasNext()) {
			w = next();
			switch (w.type()) {
				case left_curly_bracket: entry.add(xmlElement(flag)); break;
				case EOF: break children;
				default: unexpected(w.val(), "<");
			}
		}

		return entry;
	}

	@SuppressWarnings("fallthrough")
	public XElement xmlElement(int flag) throws ParseException {
		String name = except(LITERAL, "元素名").val();

		XElement entry = createElement(name);
		boolean needCloseTag = true;

		Word w = next();
		attributes:
		while (true) {
			switch (w.type()) {
				case slash: needCloseTag = false; except(right_curly_bracket, ">");
				case right_curly_bracket:
					entry.likeClose = needCloseTag;

					// <img />之类的不能包含其他内容的
					if (!needCloseTag || !needCLOSE.test(name)) return entry;

					break attributes;

				case LITERAL: w = readAttribute(entry, w); break;
				default: unexpected(w.val(), "属性 / '>'");
			}
		}

		this.flag |= _SKIP_CONST_STRING;
		w = next();
		o:
		while (w.type() != EOF) {
			switch (w.type()) {
				case element_end:
					this.flag &= ~_SKIP_CONST_STRING;

					String nameEnd = except(LITERAL, "元素名").val();
					if (!nameEnd.equals(name)) {
						if ((flag & LENIENT) == 0) throw err("结束标签不匹配! 需要 " + name + " 找到 " + w.val());
						errorTag = w.val();
					}
					break o;
				case left_curly_bracket:
					try {
						this.flag &= ~_SKIP_CONST_STRING;
						entry.add(xmlElement(flag));
						this.flag |= _SKIP_CONST_STRING;
					} catch (ParseException e) {
						throw e.addPath('.'+name+'['+entry.size()+']');
					}
					w = next();
					break;
				case CDATA_STRING:
					XText text = new XText(w.val());
					text.CDATA_flag = XText.ALWAYS_ENCODE;
					entry.add(text);
					w = next();
					break;
				default:
					index = lastWordBegin;
					text = readString();
					if (text == null) throw err("未预料的文件尾");
					entry.add(text);
					w = next();
					break;
			}
		}

		if ((flag & LENIENT) == 0 || hasNext()) except(right_curly_bracket, ">");

		return entry;
	}

	protected XElement createElement(String name) {
		return new XElement(name);
	}

	protected Word readAttribute(XElement entry, Word w) throws ParseException {
		String attr_name = w.val();
		w = next();

		if (w.type() == equ) {
			entry.attr(attr_name, of(next()));
			w = next();
		} else {
			entry.attr(attr_name, CString.valueOf(""));
		}
		return w;
	}

	@SuppressWarnings("fallthrough")
	public static boolean literalSafe(CharSequence text) {
		if (LITERAL_UNSAFE) return false;
		if (text.length() == 0) return false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<' || c == '&') return false;
		}
		return true;
	}

	public static CEntry of(Word w) {
		switch (w.type()) {
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case NULL: return CNull.NULL;
			case DOUBLE:
			case FLOAT: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInteger.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case STRING:
			case LITERAL: return CString.valueOf(w.val());
		}
		throw new IllegalArgumentException("不是简单类型:" + w);
	}

	final CEntry element(int flag) { return null; }

	public Predicate<String> needCLOSE = Helpers.alwaysTrue();
	String errorTag;

	public XMLParser setCloseTagPredicate(Predicate<String> p) {
		this.needCLOSE = p == null ? Helpers.alwaysTrue() : p;
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i++);
			switch (XML_FC.getOrDefaultInt(c, 0)) {
				case C_WHITESPACE: break;
				case C_MAY__NUMBER_SIGN:
					if (i < in.length() && NUMBER.contains(in.charAt(i))) {
						index = i-1;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default:
				case C_DEFAULT: index = i-1; return readLiteral();
				case C_NUMBER: index = i-1; return readDigit(false);
				case C_SYH:
					if ((flag & _SKIP_CONST_STRING) != 0) return readLiteral();
					index = i;
					return formClip(STRING, readSlashString(c, (flag & UNESCAPED_SINGLE_QUOTE) == 0 || c == '"'));
				case 2:
					index = i-1;
					if (TextUtil.regionMatches("!--", 0, in, i)) { // <!--
						int j = TextUtil.gIndexOf(in, "-->", i+3, in.length());
						if (j < 0) throw err("在注释结束前遇到了文件尾");
						i = j+2;
						continue;
					} else if (TextUtil.regionMatches("![CDATA[", 0, in, i)) { // CDATA
						i += 8;
						int j = TextUtil.gIndexOf(in, "]]>", i, in.length());
						if (j < 0) throw err("在CDATA结束前遇到了文件尾");

						index = j+2;
						return formClip(CDATA_STRING, in.subSequence(i, j));
					}
					return readLiteral();
			}
		}
		index = i;
		return eof();
	}

	@SuppressWarnings("fallthrough")
	private XText readString() {
		CharSequence in = input;
		int i = index;

		int prevI = i;
		while (i < in.length()) {
			int c = in.charAt(i);
			if (c == '<') break;
			i++;
		}
		if (i == prevI) return null;

		index = i;
		CharList v = found; v.clear();

		if ((flag & PROCESS_ENTITY) != 0) throw new UnsupportedOperationException("PROCESS_ENTITY flag Not implemented yet");
		return new XText(v.append(in, prevI, i).toString());
	}

	@Override
	protected Word onInvalidNumber(char value, int i, String reason) throws ParseException {
		return readLiteral();
	}
}
