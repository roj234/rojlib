package roj.config;

import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.config.serial.ToXml;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.Interner;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;

import static roj.config.JSONParser.*;
import static roj.config.Word.*;

/**
 * XML - 可扩展标记语言（EXtensible Markup Language）
 *
 * @author Roj234
 */
public class XMLParser extends Parser {
	private static final short
		COMMENT = 11, CDATA_STRING = 12,
		tag_start = 13, tag_start_close = 14, tag_end = 15, tag_end_close = 16,
		header_start = 17, header_end = 18, equ = 19;

	private static final TrieTree<Word> XML_TOKENS = new TrieTree<>();
	private static final MyBitSet XML_LENDS = new MyBitSet();
	static {
		addKeywords(XML_TOKENS, TRUE, "true", "false");
		addSymbols(XML_TOKENS, XML_LENDS, tag_start, "<", "</", ">", "/>", "<?", "?>", "=");
		addWhitespace(XML_LENDS);

		XML_TOKENS.put("<!--", new Word().init(COMMENT, -99, "-->"));
		XML_TOKENS.put("<![CDATA[", new Word().init(CDATA_STRING, -98,"]]>"));
	}

	{ tokens = XML_TOKENS; literalEnd = XML_LENDS; firstChar = SIGNED_NUMBER_C2C; }

	public static final int LENIENT = 1, HTML = 2, DECODE_ENTITY = 4, KEEP_SPACE = 8;
	private static final MyHashSet<String> HTML_SHORT_TAGS = new MyHashSet<>(TextUtil.split("area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr", '|'));

	public static Document parses(CharSequence string) throws ParseException { return new XMLParser().parseToXml(string, LENIENT); }
	public static Document parses(CharSequence string, int flag) throws ParseException { return new XMLParser().parseToXml(string, flag); }

	@Override
	public final Map<String, Integer> dynamicFlags() { return Map.of("Lenient", LENIENT, "HTML", HTML, "DecodeEntity", DECODE_ENTITY, "KeepSpace", KEEP_SPACE); }
	@Override
	public final ConfigMaster format() { return ConfigMaster.XML; }

	public Document parseToXml(CharSequence text, int flag) throws ParseException {
		ToXml entry = new ToXml();
		parse(text, flag, entry);
		return (Document) entry.get();
	}

	public Document parseToXml(File file, int flag) throws ParseException, IOException {
		ToXml entry = new ToXml();
		parse(file, flag, entry);
		return (Document) entry.get();
	}

	@Override
	public CEntry parse(CharSequence text, int flag) throws ParseException {
		ToEntry entry = new ToEntry();
		parse(text, flag, entry);
		return entry.get();
	}

	private CVisitor cc;

	@Override
	public <CV extends CVisitor> CV parse(CharSequence text, int flag, CV cv) throws ParseException {
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
			retractWord();
			cc.setProperty("xml:headless", true);
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
				if ((flag & LENIENT) == 0 || w.type() == EOF)
					break;
			}
			ccXmlElem();
		}

		if (w.type() != EOF) unexpected(w.val(), "eof");

		cc.pop();
	}

	private void ccXmlElem() throws ParseException {
		String name = Interner.intern(except(LITERAL, "元素名称").val());

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

		if (w.type() == tag_end_close || name.equals("!DOCTYPE") || !needCLOSE.test(name)) {
			cc.setProperty("xml:short_tag", true);
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
					case CDATA_STRING: cc.value(w.val()); cc.setProperty("xml:cdata", true); break;
					case LITERAL: cc.value(w.val()); break;
					case EOF:
						if ((flag & LENIENT) != 0) {
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
			if ((flag & LENIENT) == 0) throw err("结束标签不匹配! 需要 "+name+" 找到 "+w.val());
			errorTag = w.val();
		}
		except(tag_end, ">");

		cc.pop();
	}

	private Word readAttribute(Word w) throws ParseException {
		cc.key(Interner.intern(w.val()));
		w = next();

		if (w.type() == equ) {
			attrVal(next()).accept(cc);
			return next();
		} else {
			cc.valueNull();
			return w;
		}
	}

	@SuppressWarnings("fallthrough")
	public static boolean literalSafe(CharSequence text) {
		if (ALWAYS_ESCAPE) return false;
		if (text.length() == 0) return false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<' || c == '&') return false;
		}
		return true;
	}

	public static CEntry attrVal(Word w) {
		switch (w.type()) {
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case NULL: return CNull.NULL;
			case FLOAT: return CFloat.valueOf(w.asFloat());
			case DOUBLE: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInt.valueOf(w.asInt());
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
				default:
					prevIndex = index = i;
					Word w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;continue;}
					return w;
				case C_NUMBER: prevIndex = index = i; return readDigit(false);
				case C_STRING:
					prevIndex = i;
					index = i+1;
					return formClip(STRING, readSlashString(c, true));
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
		if (i == in.length()) return eof();
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

		if (in.charAt(i) == '<' && ((flag&KEEP_SPACE) == 0 || prevI == i)) {
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

		boolean hasEntity = false;
		while (i < in.length()) {
			c = in.charAt(i);
			if (c == '<') {
				// 如果是comment，那么拆成两个字符串块是应该的吗？
				// 20240319 我暂时感觉是应该的，因为某些解析器中comment也是element
				break;
			}
			if (c == '&') hasEntity = true;

			i++;

			if (!WHITESPACE.contains(c)) lastNonEmpty = i;
		}

		if (hasEntity && (flag&DECODE_ENTITY) != 0) {
			Escape.deHtmlEntities_Append(v, in.subSequence(prevI, lastNonEmpty));
		} else {
			v.append(in, prevI, lastNonEmpty);
		}

		if (lastNonEmpty != i || (flag&KEEP_SPACE) != 0) v.append(' ');

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

	@Override
	protected final Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
}