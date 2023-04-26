package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.config.serial.ToXEntry;
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
public class XMLParser extends Parser<CList> {
	static final short
		tag_start = 13, tag_end = 14, tag_end_close = 15,
		equ = 16, ask = 17,
		tag_start_close = 18, header_start = 19,
		header_end = 20, CDATA_STRING = 21;

	private static final TrieTree<Word> XML_TOKENS = new TrieTree<>();
	private static final MyBitSet XML_LENDS = new MyBitSet();
	private static final Int2IntMap XML_FC = new Int2IntMap();
	static {
		addKeywords(XML_TOKENS, 10, "true", "false");
		addSymbols(XML_TOKENS, XML_LENDS, 13, "<", ">", "/>", "=", "?", "</", "<?", "?>");
		addWhitespace(XML_LENDS);

		fcSetDefault(XML_FC, 3);
		XML_FC.put('\'', C_SYH);
		XML_FC.put('<', 2); // magic number in switch
	}

	{ tokens = XML_TOKENS; literalEnd = XML_LENDS; firstChar = XML_FC; }

	public static final int FORCE_XML_HEADER = 1, LENIENT = 2, HTML = 8, PROCESS_ENTITY = 16;
	private static final MyHashSet<String> HTML_SHORT_TAGS = new MyHashSet<>(TextUtil.split("!doctype|br|img|link|input|source|track|param", '|'));

	public static XHeader parses(CharSequence string) throws ParseException {
		return new XMLParser().parseToXml(string, LENIENT);
	}
	public static XHeader parses(CharSequence string, int flag) throws ParseException {
		return new XMLParser().parseToXml(string, flag);
	}

	@Override
	public final int availableFlags() { return FORCE_XML_HEADER | LENIENT | UNESCAPED_SINGLE_QUOTE | PROCESS_ENTITY; }

	@Override
	public final String format() { return "XML"; }

	public XHeader parseToXml(CharSequence text, int flag) throws ParseException {
		ToXEntry entry = new ToXEntry();
		parse(entry, text, flag);
		return (XHeader) entry.get();
	}

	@Override
	public CList parse(CharSequence text, int flag) throws ParseException {
		ToEntry entry = new ToEntry();
		parse(entry, text, flag);
		return entry.get().asList();
	}

	private CVisitor cc;

	@Override
	public <CV extends CVisitor> CV parse(CV cv, CharSequence text, int flag) throws ParseException {
		this.flag = flag;
		cc = cv;
		init(text);
		try {
			if ((flag & HTML) != 0) needCLOSE = (s) -> !HTML_SHORT_TAGS.contains(s.toLowerCase());
			ccXmlHeader();
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			cc = null;
			input = null;
		}
		return cv;
	}

	private void ccXmlHeader() throws ParseException {
		cc.valueList();

		Word w = next();
		if (w.type() != header_start) {
			if ((flag & FORCE_XML_HEADER) != 0) unexpected(w.val(), "<?xml");
			retractWord();
			cc.vsopt("xml:headless", true);
		} else {
			if (!readLiteral().val().equalsIgnoreCase("xml")) {
				throw err("<? 但不是 <?xml");
			}

			w = next();
			if (w.type() != header_end) {
				cc.valueMap();
				do {
					if (w.type() != LITERAL) {
						if (w.type() != STRING || (flag & LENIENT) == 0)
							throw err("期待属性名称");
					}
					w = readAttribute(w);
				} while (w.type() != header_end);
				cc.pop();
			}
		}

		while (true) {
			w = next();
			if (w.type() != tag_start) {
				if ((flag & HTML) == 0 || w.type() == EOF)
					break;
			}
			ccXmlElem();
		}

		if (w.type() != EOF) unexpected(w.val(), "eof");

		cc.pop();
	}

	private void ccXmlElem() throws ParseException {
		String name = except(LITERAL, "元素名称").val();

		cc.valueList();
		cc.value(name);

		Word w = next();

		if (w.type() != tag_end_close && w.type() != tag_end) {
			cc.valueMap();
			do {
				if (w.type() != LITERAL) {
					if (w.type() != STRING || (flag & LENIENT) == 0)
						throw err("期待属性名称");
				}
				w = readAttribute(w);
			} while (w.type() != tag_end_close && w.type() != tag_end);
			cc.pop();
		}

		if (w.type() == tag_end_close || !needCLOSE.test(name)) {
			cc.vsopt("xml:short_tag", true);
			cc.pop();
			return;
		}

		int i = 0;

		w = readValue();
		flushBefore(index);

		if (w.type() != tag_start_close) {
			cc.valueList();

			label:
			while (true) {
				switch (w.type()) {
					case tag_start_close: break label;
					case tag_start:
						try {
							ccXmlElem();
						} catch (ParseException e) {
							throw e.addPath('.'+name+'['+i+']');
						}
						break;
					case CDATA_STRING: cc.value(w.val()); cc.vsopt("xml:cdata", true); break;
					case LITERAL: cc.value(w.val()); break;
					case EOF:
						if ((flag & HTML) != 0) {
							cc.pop();
							cc.pop();
							return;
						}
						throw err("未预料的文件尾");
					default: unexpected(w.val());
				}

				i++;
				w = readValue();
				flushBefore(index);
			}

			cc.pop();
		}

		if (!name.equals(except(LITERAL, "元素名称").val())) {
			if ((flag & LENIENT) == 0) throw err("结束标签不匹配! 需要 " + name + " 找到 " + w.val());
			errorTag = w.val();
		}
		except(tag_end, ">");

		cc.pop();
	}

	private Word readAttribute(Word w) throws ParseException {
		cc.key(w.val());
		w = next();

		if (w.type() == equ) {
			of(next()).forEachChild(cc);
			return next();
		} else {
			cc.valueNull();
			return w;
		}
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

	@Deprecated
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

	public Predicate<String> needCLOSE = Helpers.alwaysTrue();
	public String errorTag;

	public XMLParser setCloseTagPredicate(Predicate<String> p) {
		this.needCLOSE = p == null ? Helpers.alwaysTrue() : p;
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (XML_FC.getOrDefaultInt(c, 0)) {
				case C_WHITESPACE: break;
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i+1))) {
						index = i;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default:
				case C_DEFAULT: index = i; return readSymbol();
				case C_NUMBER: index = i; return readDigit(false);
				case C_SYH:
					index = i+1;
					return formClip(STRING, readSlashString(c, (flag & UNESCAPED_SINGLE_QUOTE) == 0 || c == '"'));
				case 2:
					index = i;
					int j = checkCommentOrCDATA(i+1, false);
					if (j > 0) formClip(CDATA_STRING, in.subSequence(i+9, j));
					if (j == 0) return readSymbol();
					i = index;
					continue;
			}
			i++;
		}
		index = i;
		return eof();
	}

	final Word readValue() throws ParseException {
		CharSequence in = input;
		int i = index;
		int prevI = i;

		int c = 0;
		while (i < in.length()) {
			c = in.charAt(i);
			if (!WHITESPACE.contains(c)) break;
			i++;
		}
		index = i;
		if (i == in.length()) return eof();

		while (c == '<') {
			c = checkCommentOrCDATA(i+1, false);
			if (c > 0) return formClip(CDATA_STRING, in.subSequence(i+9, c));

			i = index;
			if (c == 0) break;
			c = in.charAt(i);
		}

		Word w = tryMatchToken();
		if (w != null) return w;

		CharList v = found; v.clear();

		// restore whitespace
		i = prevI;

		findstr:
		while (i < in.length()) {
			c = in.charAt(i);
			while (c == '<') {
				v.append(in, prevI, i);

				c = checkCommentOrCDATA(i+1, true);
				if (c >= 0) {
					prevI = i;
					break findstr;
				}

				prevI = i = index;
				continue findstr;
			}

			if (c == '&' && (flag & PROCESS_ENTITY) != 0) {
				v.append(in, prevI, i++);

				int j = i;
				while (j < in.length()) {
					c = in.charAt(j);
					if (c == ';') break;
					j++;
				}

				handleAmp(in.subSequence(i, j), v);
				prevI = i = j;
			} else {
				i++;
			}
		}
		v.append(in, prevI, i);

		index = i;
		return formClip(LITERAL, v);
	}

	private int checkCommentOrCDATA(int i, boolean quick) throws ParseException {
		CharSequence in = input;
		if (TextUtil.regionMatches("!--", 0, in, i)) { // <!--
			int j = TextUtil.gIndexOf(in, "-->", i +3, in.length());
			if (j < 0) throw err("在注释结束前遇到了文件尾");
			index = j+3;
			return -1;
		} else if (TextUtil.regionMatches("![CDATA[", 0, in, i)) { // CDATA
			if (quick) return 1;

			int j = TextUtil.gIndexOf(in, "]]>", i +8, in.length());
			if (j < 0) throw err("在CDATA结束前遇到了文件尾");

			index = j+3;
			return j;
		}

		return 0;
	}

	protected void handleAmp(CharSequence seq, CharList out) throws ParseException {
		throw err("未实现");
		//&lt;	<	小于
		//&gt;	>	大于
		//&amp;	&	和号
		//&apos;	'	单引号
		//&quot;	"	引号
	}

	@Override
	protected final Word onInvalidNumber(char value, int i, String reason) throws ParseException { return readLiteral(); }
}
