package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.BitSet;
import roj.collect.HashSet;
import roj.collect.TrieTree;
import roj.config.node.BoolValue;
import roj.config.node.ConfigValue;
import roj.config.node.NullValue;
import roj.config.node.xml.Document;
import roj.text.*;
import roj.util.Helpers;

import java.util.function.Predicate;

import static roj.config.JsonParser.*;
import static roj.text.Token.*;

/**
 * XML - 可扩展标记语言（EXtensible Markup Language）
 *
 * @author Roj234
 */
public class XmlParser extends TextParser {
	private static final short
		COMMENT = 11, CDATA_STRING = 12,
		tag_start = 13, tag_start_close = 14, tag_end = 15, tag_end_close = 16,
		procInsnBegin = 17, procInsnEnd = 18, equ = 19, declaration = 20;

	private static final TrieTree<Token> XML_TOKENS = new TrieTree<>();
	private static final BitSet XML_LENDS = new BitSet();
	static {
		addKeywords(XML_TOKENS, TRUE, "true", "false");
		addSymbols(XML_TOKENS, XML_LENDS, tag_start, "<", "</", ">", "/>", "<?", "?>", "=", "<!");
		addWhitespace(XML_LENDS);

		XML_TOKENS.put("<!--", new Token().init(COMMENT, -99, "-->"));
		XML_TOKENS.put("<![CDATA[", new Token().init(CDATA_STRING, -98,"]]>"));
	}

	public static final int HTML = 4, SKIP_DECODE_ENTITY = 8, PRESERVE_SPACE = 16, NO_MY_SPACE = 32;
	//https://html.spec.whatwg.org/#void-elements
	private static final HashSet<String> HTML_VOID_ELEMENTS = new HashSet<>(TextUtil.split("area,base,br,col,embed,hr,img,input,link,meta,source,track,wbr",','));

	public XmlParser() {this(0);}
	public XmlParser(@MagicConstant(flags = {LENIENT, HTML, SKIP_DECODE_ENTITY, PRESERVE_SPACE, NO_MY_SPACE}) int flags) {
		super(flags);
		tokens = XML_TOKENS;
		literalEnd = XML_LENDS;
		firstChar = SIGNED_NUMBER_C2C;
	}

	@Override
	public Document parse(CharSequence text) throws ParseException {
		XmlEmitter entry = new XmlEmitter();
		parse(text, entry);
		return (Document) entry.get();
	}

	private ValueEmitter emitter;

	@Override
	public void parse(CharSequence text, ValueEmitter emitter) throws ParseException {
		flags &= ~PRESERVE_SPACE;
		this.emitter = emitter;
		init(text);
		try {
			if ((flags & HTML) != 0) needCLOSE = (s) -> !HTML_VOID_ELEMENTS.contains(s.toLowerCase());
			parseDocument();
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			this.emitter = null;
			input = null;
		}
	}

	private void parseDocument() throws ParseException {
		emitter.emitList();

		Token w = next();
		if (w.type() != procInsnBegin) {
			retractWord();
			emitter.setProperty(XmlEmitter.HEADLESS, true);
		} else {
			if (!readLiteral().text().equalsIgnoreCase("xml")) {
				throw err("<? 但不是 <?xml");
			}

			w = next();
			if (w.type() != procInsnEnd) {
				emitter.emitMap();
				do {
					if (w.type() != LITERAL && (flags & LENIENT) == 0)
						throw err("无效的属性名称: "+w);
					w = parseAttribute(w);
				} while (w.type() != procInsnEnd);
				emitter.pop();
			}
		}

		w = next();
		if (w.type() == declaration) {
			w = next();
			if (w.type() != LITERAL || !w.text().equalsIgnoreCase("DOCTYPE")) {
				throw err("期待DOCTYPE指令");
			}
			parseDoctype();
			w = next();
		}

		while (true) {
			if (w.type() != tag_start) {
				if (w.type() == COMMENT) {
					emitter.comment(w.text());
					w = next();
					continue;
				}

				if ((flags & LENIENT) == 0 || w.type() == EOF)
					break;
			}
			parseElement();
			w = next();
		}

		if (w.type() != EOF) unexpected(w.text(), "eof");

		emitter.pop();
	}

	private void parseDoctype() throws ParseException {
		emitter.emitList();
		emitter.emit("!DOCTYPE");
		emitter.emitMap();

		Token w;
		do {
			w = next();
			if (w.type() == tag_end) break;
			emitter.emitKey(w.text());
			emitter.emitNull();
		} while (true);

		emitter.pop();
		emitter.pop();
	}

	private void parseElement() throws ParseException {
		String name = except(LITERAL, "元素名称").text();

		emitter.emitList();
		emitter.emit(name);

		Token w = next();

		var prevFlag = flags;
		if (w.type() != tag_end_close && w.type() != tag_end) {
			emitter.emitMap();
			do {
				if (w.type() != LITERAL && (flags & LENIENT) == 0)
					throw err("无效的属性名称: "+w);
				w = parseAttribute(w);
			} while (w.type() != tag_end_close && w.type() != tag_end);
			emitter.pop();
		}

		if (w.type() == tag_end_close || !needCLOSE.test(name)) {
			emitter.setProperty(XmlEmitter.SHORT_TAG, true);
			emitter.pop();
			flags = prevFlag;
			return;
		}

		int i = 0;

		w = readElementValue();
		flushBefore(index);

		if (w.type() != tag_start_close) {
			emitter.emitList();

			loop:
			while (true) {
				switch (w.type()) {
					case tag_start_close: break loop;
					case tag_start:
						try {
							parseElement();
						} catch (ParseException e) {
							throw e.addPath('.'+name+'['+i+']');
						}
						break;
					case COMMENT: emitter.comment(w.text()); break;
					case CDATA_STRING: emitter.emit(w.text()); emitter.setProperty(XmlEmitter.CDATA, true); break;
					case LITERAL: emitter.emit(w.text()); break;
					case EOF:
						if ((flags & LENIENT) != 0) {
							emitter.pop();
							emitter.pop();
							return;
						}
						throw err("未预料的文件尾");
					default: unexpected(w.text());
				}

				i++;
				w = readElementValue();
				flushBefore(index);
			}

			emitter.pop();
		}

		if (!name.equals(except(LITERAL, "元素名称").text())) {
			if ((flags & LENIENT) == 0) throw err("结束标签不匹配! 需要 "+name+" 找到 "+w.text());
			errorTag = w.text();
		}
		except(tag_end, ">");

		emitter.pop();
		flags = prevFlag;
	}

	private Token parseAttribute(Token w) throws ParseException {
		var key = w.text();
		emitter.emitKey(key);
		w = next();

		if (w.type() == equ) {
			var val = attrVal(next());
			if (key.startsWith("xml:")) handleSystemAttribute(key, val);
			val.accept(emitter);
			return next();
		} else {
			emitter.emitNull();
			return w;
		}
	}
	private void handleSystemAttribute(String key, ConfigValue val) {
		if (key.equals("xml:space")) {
			if (val.asString().equalsIgnoreCase("preserve")) {
				flags |= PRESERVE_SPACE;
			} else {
				flags &= ~PRESERVE_SPACE;
			}
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

	public static ConfigValue attrVal(Token w) {
		return switch (w.type()) {
			case TRUE -> BoolValue.TRUE;
			case FALSE -> BoolValue.FALSE;
			case NULL -> NullValue.NULL;
			case FLOAT -> ConfigValue.valueOf(w.asFloat());
			case DOUBLE -> ConfigValue.valueOf(w.asDouble());
			case INTEGER -> ConfigValue.valueOf(w.asInt());
			case LONG -> ConfigValue.valueOf(w.asLong());
			case STRING, LITERAL -> ConfigValue.valueOf(w.text());
			default -> throw new IllegalArgumentException("不是简单类型:"+w);
		};
	}

	public Predicate<String> needCLOSE = Helpers.alwaysTrue();
	public String errorTag;

	public XmlParser setCloseTagPredicate(Predicate<String> p) {
		this.needCLOSE = p == null ? Helpers.alwaysTrue() : p;
		return this;
	}

	/**
	 * 将开头结尾空格替换为一个的字符串，或元素
	 */
	private Token readElementValue() throws ParseException {
		CharSequence in = input;
		int i = index;
		if (i == in.length()) return eof();
		int prevI = i;

		// skip and collect whitespace
		int c;
		if ((flags&PRESERVE_SPACE) == 0) while (true) {
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
			// and CDATA_STRING (and skipped COMMENT)
			return tryMatchToken();
		}

		// 上面部分和readWord差不多，除了不处理数字什么的
		// 下面处理字符串，读到<为止

		// 规定：不保留元素之间的纯空白字符串
		// 非空白字符串头尾的空白字符串会被替换为单个空格
		CharList v = found; v.clear();

		// restore whitespace
		if (i != prevI && (flags&NO_MY_SPACE) == 0) v.append(' ');
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

			if ((flags&PRESERVE_SPACE) != 0 || !WHITESPACE.contains(c)) lastNonEmpty = i;
		}

		if (hasEntity && (flags&SKIP_DECODE_ENTITY) == 0) {
			HtmlEntities.decode(in.subSequence(prevI, lastNonEmpty), v);
		} else {
			v.append(in, prevI, lastNonEmpty);
		}

		if (lastNonEmpty != i && (flags&NO_MY_SPACE) == 0) v.append(' ');

		index = i;
		return formClip(LITERAL, v);
	}

	@Override
	protected Token onSpecialToken(Token w) throws ParseException {
		String matcher = w.type() == CDATA_STRING ? "]]>" : "-->";
		int i;
		try {
			i = TextUtil.indexOf(input, matcher, index, input.length());
		} catch (Exception e) {
			i = -1;
		}

		if (i < 0) throw err(w.type() == CDATA_STRING ? "未结束的CDATA标签" : "未结束的注释", index);

		found.clear();
		found.append(input, index, i);
		index = i+3;
		return formClip(w.type(), found.toString());
	}
}