package roj.config;

import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.config.serial.ToXEntry;
import roj.config.word.Word;
import roj.net.http.HttpUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
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
		COMMENT = 12, CDATA_STRING = 13,
		tag_start = 14, tag_start_close = 15, tag_end = 16, tag_end_close = 17,
		header_start = 18, header_end = 19, equ = 20;

	private static final TrieTree<Word> XML_TOKENS = new TrieTree<>();
	private static final MyBitSet XML_LENDS = new MyBitSet();
	static {
		addKeywords(XML_TOKENS, 10, "true", "false");
		addSymbols(XML_TOKENS, XML_LENDS, 14, "<", "</", ">", "/>", "<?", "?>", "=");
		addWhitespace(XML_LENDS);

		XML_TOKENS.put("<!--", new Word().init(COMMENT, -99, "-->"));
		XML_TOKENS.put("<![CDATA[", new Word().init(CDATA_STRING, -98,"]]>"));
	}

	{ tokens = XML_TOKENS; literalEnd = XML_LENDS; firstChar = SIGNED_NUMBER_C2C; }

	public static final int FORCE_XML_HEADER = 1, LENIENT = 2, HTML = 8, PROCESS_ENTITY = 16;
	private static final MyHashSet<String> HTML_SHORT_TAGS = new MyHashSet<>(TextUtil.split("!doctype|br|img|link|input|source|track|param", '|'));

	public static Document parses(CharSequence string) throws ParseException {
		return new XMLParser().parseToXml(string, LENIENT);
	}
	public static Document parses(CharSequence string, int flag) throws ParseException {
		return new XMLParser().parseToXml(string, flag);
	}

	@Override
	public final int availableFlags() { return FORCE_XML_HEADER | LENIENT | UNESCAPED_SINGLE_QUOTE | PROCESS_ENTITY; }

	@Override
	public final String format() { return "XML"; }

	public Document parseToXml(CharSequence text, int flag) throws ParseException {
		ToXEntry entry = new ToXEntry();
		parse(entry, text, flag);
		return (Document) entry.get();
	}

	public Document parseToXml(File file, int flag) throws ParseException, IOException {
		ToXEntry entry = new ToXEntry();
		parseRaw(entry, file, flag);
		return (Document) entry.get();
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

		w = readElementValue();
		flushBefore(index);

		if (w.type() != tag_start_close) {
			cc.valueList();

			loop:
			while (true) {
				switch (w.type()) {
					case tag_start_close: break loop;
					case tag_start:
						try {
							ccXmlElem();
						} catch (ParseException e) {
							throw e.addPath('.'+name+'['+i+']');
						}
						break;
					case COMMENT: cc.comment(w.val()); break;
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
				w = readElementValue();
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
			case DOUBLE, FLOAT: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInteger.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case STRING, LITERAL: return CString.valueOf(w.val());
		}
		throw new IllegalArgumentException("不是简单类型:"+w);
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
			switch (SIGNED_NUMBER_C2C.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i+1))) {
						prevIndex = index = i;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default: prevIndex = index = i; return readSymbol();
				case C_NUMBER: prevIndex = index = i; return readDigit(false);
				case C_STRING:
					prevIndex = i;
					index = i+1;
					return formClip(STRING, readSlashString(c, (flag & UNESCAPED_SINGLE_QUOTE) == 0 || c == '"'));
				case C_WHITESPACE: i++;
			}
		}
		index = i;
		return eof();
	}

	/**
	 * 将开头结尾空格替换为一个的字符串，或元素
	 */
	final Word readElementValue() throws ParseException {
		CharSequence in = input;
		int i = index;
		int prevI = i;

		// skip and collect whitespace
		int c;
		while (true) {
			c = in.charAt(i);
			if (!WHITESPACE.contains(c)) break;

			if (++i == in.length()) {
				index = i;
				return eof();
			}
		}
		index = i;

		if (in.charAt(i) == '<') {
			// includes tag_start, tag_end
			// and CDATA_STRING (and skipped COMMEND)
			return tryMatchToken();
		}

		// 上面部分和readWord差不多，除了不处理数字什么的
		// 下面处理字符串，读到<为止

		// 规定：不保留元素之间的纯空白字符串
		// 非空白字符串头尾的空白字符串会被替换为单个空格
		CharList v = found; v.clear();

		// restore whitespace
		if (i != prevI) v.append(' ');
		int lastNonEmpty = prevI = i;

		while (i < in.length()) {
			c = in.charAt(i);
			if (c == '<') {
				// 如果是comment，那么拆成两个字符串块是应该的吗？
				// 20240319 我暂时感觉是应该的，因为某些解析器中comment也是element
				break;
			}

			i++;
			if (c == '&' && (flag & PROCESS_ENTITY) != 0) {
				v.append(in, prevI, i);

				int j = TextUtil.gIndexOf(in, ';', i);

				handleAmp(in.subSequence(i, j), v);
				prevI = i = j+1;
			}

			if (!WHITESPACE.contains(c)) lastNonEmpty = i;
		}
		v.append(in, prevI, lastNonEmpty);
		if (lastNonEmpty != i) v.append(' ');

		index = i;
		return formClip(LITERAL, v);
	}

	@Override
	protected Word onSpecialToken(Word w) throws ParseException {
		String matcher = w.type() == CDATA_STRING ? "]]>" : "-->";
		int i;
		try {
			i = TextUtil.gIndexOf(input, matcher, index, input.length());
		} catch (Exception e) {
			i = -1;
		}

		if (i < 0) throw err(w.type() == CDATA_STRING ? "未结束的CDATA标签" : "未结束的注释", index);

		found.clear();
		found.append(input, index, i);
		index = i+3;
		return formClip(w.type(), found.toString());
	}

	protected void handleAmp(CharSequence seq, CharList out) { HttpUtil.htmlspecial_decode_all(out, seq); }

	@Override
	protected final Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
}