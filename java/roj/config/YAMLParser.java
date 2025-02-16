package roj.config;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.text.CharList;
import roj.text.Interner;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Map;

import static roj.config.Flags.*;
import static roj.config.JSONParser.*;
import static roj.config.Word.EOF;
import static roj.config.Word.LITERAL;

/**
 * Yaml解析器
 *
 * @author Roj234
 * @since 2021/7/7 2:03
 */
public class YAMLParser extends Parser {
	static final int JSON_MODE = 16;

	static final short delim = 18,
		ask = 19,  join = 20,
		anchor = 21, ref = 22, force_cast = 23,
		multiline = 24, multiline_clump = 25;

	private static final TrieTree<Word> YAML_TOKENS = new TrieTree<>();
	// - for timestamp
	private static final MyBitSet YAML_LENDS = new MyBitSet(), TMP1 = MyBitSet.from("\r\n:#"), TMP_JSON = MyBitSet.from(":,{}[]\r\n \t");

	static {
		put(TRUE, "True", "On", "Yes");
		put(FALSE, "False", "Off", "No");
		put(NULL, "Null");
		put(EOF, "---"); // 文档起始标记
		put(EOF, "..."); // 文档结束标记
		addSymbols(YAML_TOKENS, YAML_LENDS, lBrace, "{","}","[","]",",",":","-","?","<<","&","*","!!","|",">");
		addWhitespace(YAML_LENDS);
		markSpecial(YAML_TOKENS,"&","*","!!","|",">");

		YAML_TOKENS.put("#", new Word().init(0, ST_SINGLE_LINE_COMMENT, "#"));
		YAML_TOKENS.put("'''", new Word().init(0, ST_MULTI_LINE_COMMENT, "'''"));
		YAML_TOKENS.put("'", new Word().init(0, ST_LITERAL_STRING, "'"));
		YAML_TOKENS.put("\"", new Word().init(0, ST_STRING, "\""));
	}

	private static void put(int id, String... tokens) {
		for (String token : tokens) {
			Word w = new Word().init(id, 0, token);
			YAML_TOKENS.put(token, w);
			YAML_TOKENS.put(token.toLowerCase(), w);
			YAML_TOKENS.put(token.toUpperCase(), w);
		}
	}
	{ tokens = YAML_TOKENS; literalEnd = YAML_LENDS; }

	public YAMLParser() {}
	public YAMLParser(int flag) {super(flag);}

	public Map<String, Integer> dynamicFlags() { return Map.of("OrderedMap", ORDERED_MAP,  "Lenient", LENIENT, "NoDuplicateKey", NO_DUPLICATE_KEY); }
	public final ConfigMaster format() { return ConfigMaster.YAML; }

	/**
	 * 解析行式数组定义 <BR>
	 * - xx : yy
	 * - cmw :
	 * - xyz
	 */
	private CList yamlLineArray() throws ParseException {
		CList list = new CList();

		int superIndent = prevIndent;
		int firstIndent = indent;
		if (firstIndent < superIndent) throw err("下级缩进("+firstIndent+")<上级("+superIndent+")");

		while (true) {
			int line = LN, i = index;
			Word w = next();
			if (LN > line && indent <= firstIndent && whiteSpaceUntilNextLine(i)) {
				list.add(CNull.NULL);
			} else {
				retractWord();
				try {
					prevIndent = firstIndent;
					list.add(element(flag));
				} catch (ParseException e) {
					throw e.addPath("["+list.size()+"]");
				} finally {
					prevIndent = superIndent;
				}
				w = next();
			}

			int off;
			// 第二个判断检查 - - val
			if (w.type() != delim || superIndent == -2 || (off = indent) < firstIndent) {
				retractWord();
				break;
			} else if (off != firstIndent) throw err("缩进有误:"+off+"/"+firstIndent);
		}

		return list;
	}

	/**
	 * 解析对象定义 <BR>
	 * a : r \r\n
	 * c : x
	 */
	private CEntry yamlObject(Word w) throws ParseException {
		Map<String, CEntry> map = (flag & ORDERED_MAP) != 0 ? new LinkedMyHashMap<>() : new MyHashMap<>();
		Map<String, String> comment = null;

		int superIndent = prevIndent;
		int firstIndent = indent;
		if (firstIndent <= superIndent) throw err("下级缩进("+firstIndent+")<=上级("+superIndent+")");

		cyl:
		while (true) {
			switch (w.type()) {
				case ask: throw err("配置文件不用字符串做key是坏文明");
				case join:
					// <<: *xxx
					// 固定搭配
					except(colon, ":");
					except(ref, "*");
					CEntry entry = anchors.get(w.val());
					if (entry == null) throw err("不存在的锚点 "+w.val());
					if (!entry.mayCastTo(Type.MAP)) throw err("锚点 "+w.val()+" 无法转换为map");
					map.putAll(entry.asMap().raw());
				break;
				case Word.LITERAL, Word.STRING:
				case Word.INTEGER, Word.LONG, Word.DOUBLE, Word.FLOAT:
				case NULL:
					String name = Interner.intern(w.val());
					except(colon, ":");
					comment = addComment(comment, name);

					try {
						prevIndent = firstIndent;
						if (map.put(name, element(flag)) != null) {
							if ((flag & NO_DUPLICATE_KEY) != 0) throw err("重复的key: "+name);
						}
					} catch (ParseException e) {
						throw e.addPath('.'+name);
					} finally {
						prevIndent = superIndent;
					}
				break;
				case Word.EOF: break cyl;
				default: unexpected(w.val(), "字符串");
			}

			w = nextNN();
			if (w.type() == EOF) break;

			int indent = this.indent;
			if (indent < firstIndent) {
				retractWord();
				break;
			} else if (indent != firstIndent) throw err("缩进有误:"+indent+"/"+firstIndent);
		}

		clearComment();
		return comment == null ? new CMap(map) : new CCommMap(map, comment);
	}

	public CEntry parse(CharSequence text, int flag) throws ParseException {
		this.flag = flag;
		init(text);
		if (!next().val().equals("---")) retractWord();
		try {
			return element(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}
	final CEntry element(int flag) throws ParseException {
		Word w = next();
		String cnt = w.val();
		switch (w.type()) {
			case force_cast: {
				CEntry val = element(flag);
				switch (cnt) {
					case "str": return CEntry.valueOf(val.asString());
					case "float": return CEntry.valueOf(val.asDouble());
					case "int": return CEntry.valueOf(val.asInt());
					case "bool": return CEntry.valueOf(val.asBool());
					case "long": return CEntry.valueOf(val.asLong());
					case "map": return val.asMap();
					case "set":
						CMap map = val.asMap();
						for (CEntry entry1 : map.values()) {
							if (entry1 != CNull.NULL) throw err("无法转换为set: 值不是null: "+entry1);
						}
						return map;
					default: throw err("未知的转换目标, 不在[str float int bool map set]范围内: "+cnt);
				}
			}
			case lBracket:
				this.flag |= JSON_MODE;
				CList v = list(this, new CList(), flag);
				this.flag ^= JSON_MODE;
				return v;
			case lBrace:
				this.flag |= JSON_MODE;
				CMap v2 = map(this, flag);
				this.flag ^= JSON_MODE;
				return v2;
			case multiline: case multiline_clump: return CEntry.valueOf(cnt);
			case Word.STRING, Word.LITERAL: {
				CEntry map = checkMap();
				return map != null ? map : CEntry.valueOf(cnt);
			}
			case Word.DOUBLE, Word.FLOAT: {
				double number = w.asDouble();
				CEntry map = checkMap();
				return map != null ? map : CEntry.valueOf(number);
			}
			case Word.INTEGER: {
				int number = w.asInt();
				CEntry map = checkMap();
				return map != null ? map : CEntry.valueOf(number);
			}
			case Word.RFCDATE_DATE: return new CDate(w.asLong());
			case Word.RFCDATE_DATETIME, Word.RFCDATE_DATETIME_TZ: return new CTimestamp(w.asLong());
			case Word.LONG: return CEntry.valueOf(w.asLong());
			case TRUE, FALSE: {
				boolean b = w.type() == TRUE;
				CEntry map = checkMap();
				return map != null ? map : CEntry.valueOf(b);
			}
			case NULL: {
				CEntry map = checkMap();
				return map != null ? map : CNull.NULL;
			}
			case join: {
				CEntry map = checkMap();
				if (map == null) throw err("未预料的<<");
				return map;
			}
			case delim:
				if (prevLN == LN && LN != 1) {
					if ((flag&LENIENT) == 0) throw err("一行内不允许放置多级列表 (你看的不累吗) (通过LENIENT参数关闭该限制)");
					prevIndent = -2;
				}
				return yamlLineArray();
			case anchor: {
				CEntry val = element(flag);
				anchors.put(cnt, val);
				return val;
			}
			case ref: {
				CEntry val = anchors.get(cnt);
				if (val == null) throw err("不存在的锚点 "+cnt);
				return val;
			}
			case Word.EOF: return CNull.NULL;
			default: unexpected(cnt); return Helpers.nonnull();
		}
	}

	final Word tmpKey = new Word();
	private CEntry checkMap() throws ParseException {
		mark();

		Word firstKey = tmpKey.init(wd.type(), wd.pos(), wd.val());

		int ln = prevLN;
		if (nextNN().type() == colon) {
			if (indent <= prevIndent) {
				if (prevLN == ln) {
					if ((flag&LENIENT) == 0) throw err("一行内不允许同时放置列表和映射 (通过LENIENT参数关闭该限制)");
					// 算了，这样更简单..
					int begin = firstKey.pos();
					while (begin > 0) {
						indent++;
						if (input.charAt(--begin) == '-') break;
					}
				} else {
					retract();
					retractWord();
					return CNull.NULL;
				}
			}

			retract();
			return yamlObject(firstKey);
		}

		retract();
		return null;
	}

	// -1:true 0:false 1:ln 2:unchecked
	public static int literalSafe(CharSequence text, boolean isValue) {
		if (ALWAYS_ESCAPE) return 0;

		if (text.length() == 0) return 0;

		char c = text.charAt(0);
		// BTW? ref: https://verytoolz.com/yaml-formatter.html
		if (c == '!') return 2;

		if (c == '\'' || c == '"') return 2;

		if (WHITESPACE.contains(text.charAt(text.length()-1)) || WHITESPACE.contains(c)) return 2;

		if (YAML_TOKENS.startsWith_R(text)) {
			if (YAML_LENDS.contains(c) || (isValue && YAML_TOKENS.containsKey(text))) // trueN, falseN, nullX
				return 0;
		}
		if (TextUtil.isNumber(text) >= 0) return 0;

		for (int i = 1; i < text.length(); i++) {
			c = text.charAt(i);
			if (c == '\n') return 1;
			if (c == '#') return 0;
			if (i+1 >= text.length() || WHITESPACE.contains(text.charAt(i+1))) {
				if (c == ':' || c == '-') {
					return TextUtil.gIndexOf(text, '\n') < 0 ? 0 : 1;
				}
			}
		}

		return -1;
	}

	final MyHashMap<String, CEntry> anchors = new MyHashMap<>();
	int indent, prevIndent;

	@Override
	public final YAMLParser init(CharSequence seq) {
		anchors.clear();
		prevIndent = -1;
		indent = 0;
		super.init(seq);
		LN = 1;
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;
		int _indent = 0;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (firstChar.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i+1))) {
						prevIndex = index = i;
						if (_indent > 0) indent = i - _indent;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default:
					prevIndex = index = i;
					if (_indent > 0) indent = i - _indent;

					Word w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;_indent = i;continue;}
					return w;
				case C_NUMBER:
					if (_indent > 0) indent = i - _indent;
					prevIndex = index = i;
					return readDigit(false);
				case C_WHITESPACE:
					i++;
					if (c == '\n') _indent = i;
			}
		}

		index = i;
		return eof();
	}

	protected final Word nextNN() throws ParseException {
		firstChar = NONE_C2C;
		Word w = next();
		firstChar = SIGNED_NUMBER_C2C;
		return w;
	}

	@SuppressWarnings("fallthrough")
	@Override
	protected final boolean isValidToken(int off, Word w) {
		if ((flag & JSON_MODE) == 0) switch (w.type()) {
			case TRUE, FALSE, NULL:
				if (!whiteSpaceUntilNextLine(index + off)) return false;
			break;
			case delim, colon:
				// :和-后面必须是WHITESPACE
				if (!WHITESPACE.contains(lookAhead(off))) return false;
			break;
			case ask, join:
				if (firstChar == SIGNED_NUMBER_C2C) return false;
			break;
		}
		return super.isValidToken(off, w);
	}
	final boolean whiteSpaceUntilNextLine(int i) {
		CharSequence in = input;
		while (i < in.length()) {
			char c = in.charAt(i++);
			if (c == '#') return true;
			if (!WHITESPACE.contains(c)) return false;
			if (c == '\r' || c == '\n') return true;
		}
		return true;
	}

	@Override
	protected final Word readDigit(boolean sign) throws ParseException {
		if ((flag & JSON_MODE) != 0) return digitReader(sign, DIGIT_HBO);

		Word w = digitReader(sign, DIGIT_HBO);
		if (!whiteSpaceUntilNextLine(index)) {
			int move = index - prevIndex;
			index = prevIndex;
			if (move == 4 && input.charAt(prevIndex+4) == '-') {
				w = ISO8601Datetime(false);
				if (w != null) return w;
			}
			return readLiteral();
		}
		return w;
	}
	@Override
	protected final Word onInvalidNumber(int flag, int i, String reason) throws ParseException {return readLiteral();}

	protected final Word readLiteral() throws ParseException {
		// {a:b}
		if ((flag & JSON_MODE) != 0) {
			literalEnd = TMP_JSON;
			try {
				return super.readLiteral();
			} finally {
				literalEnd = YAML_LENDS;
			}
		}

		CharSequence in = input;
		int i = index;

		int prevI = i, textI = i;

		while (i < in.length()) {
			char c = in.charAt(i);
			if (TMP1.contains(c)) {
				// 对于冒号，额外要求后面是空白字符
				if (c != ':' || (i + 1 >= in.length() || WHITESPACE.contains(in.charAt(i + 1)))) {
					break;
				}
			}

			// trim
			if (!WHITESPACE.contains(c)) textI = i+1;

			i++;
		}

		index = i;

		if (prevI == textI) return eof();

		CharList v = found; v.clear();
		v.append(in, prevI, textI);

		if (v.length() == 1 && v.charAt(0) == '~') return formClip(NULL, "~");

		return formClip(LITERAL, v);
	}

	@Override
	protected final Word onSpecialToken(Word w) throws ParseException {
		CharSequence in = input;
		int i = index;
		CharList v = found; v.clear();

		/*
		 * 字符串可以写成多行，从第二行开始，必须有一个单空格缩进。换行符会被转为空格。
		 * 使用|保留换行符，也可以使用>折叠换行。
		 * +表示保留文字块末尾的换行，-表示删除字符串末尾的换行。*/
		if (w.type() > force_cast) {
			int clump = w.type() == multiline_clump ? 8 : 0;
			int keepTail = 0;

			char c = in.charAt(i);
			if (c == '+') {
				i++;
				keepTail = 1;
			} else if (c == '-') {
				i++;
				keepTail = -1;
			}

			c = in.charAt(i++);
			if (c != '\n' && (c != '\r' || in.charAt(i++) != '\n')) {
				throw err("期待换行", i);
			}

			// 然后算初始的Indent
			int longer = 0;
			int indent = i;
			while (true) {
				if (i == in.length()) return formClip(multiline, "");

				c = in.charAt(i);
				if (c != ' ' && c != '\t') {
					if ((longer & 1) == 0 && c == '\n' || (c == '\r' && ++i < in.length() && in.charAt(i) == '\n')) {
						v.append('\n');
						indent = ++i;
						longer |= 2;
						continue;
					}
					break;
				} else {
					longer |= 1;
				}
				i++;
			}
			indent = i-indent;

			int min = prevIndent;
			// 缩进不能为0，不然没法退出了
			if (indent <= min) {
				//a: >
				//
				//b:
				if ((longer&2) != 0) return formClip(multiline, "");

				throw err("缩进长度应大于"+min, i);
			}

			// 内容
			i = TextUtil.gAppendToNextCRLF(in, i, v);

			findNextLine:
			while (i < in.length()) {
				int prevI = i;

				int remainIndent = indent;
				while (remainIndent > 0) {
					c = in.charAt(i);
					if (c != ' ' && c != '\t') {
						v.append('\n');

						if (c == '\n' || (c == '\r' && ++i < in.length() && in.charAt(i) == '\n')) {
							if ((clump&12) == 8) {
								if ((clump&2) != 0) v.setLength(v.length()-1);
								clump |= 4;
							}
							clump &= 14;

							i++;
							continue findNextLine;
						}

						// 回退到这一行开头
						this.indent = i - prevI;
						i = prevI;
						break findNextLine;
					}
					remainIndent--;

					if (++i == in.length()) break findNextLine;
				}

				if (clump != 0) {
					if (in.charAt(i) == ' ') {
						clump &= ~1;
						//do i++; while (' ' == in.charAt(i));
					} else {
						clump |= 2;
					}

					if ((clump&9) != 0) {
						clump ^= 1;
						v.append(' ');
					} else {
						v.append('\n');
					}

					if ((clump & 2) != 0) clump |= 1;
				} else {
					v.append('\n');
				}

				prevI = v.length();
				i = TextUtil.gAppendToNextCRLF(in, i, v);
				if (v.length() == prevI) {
					if ((clump&12) == 8) {
						if ((clump&2) != 0) v.setLength(v.length()-1);

						v.append('\n');
						clump |= 4;
					}
					clump &= 14;
				} else {
					clump &= 9;
				}
			}

			if (v.length() == 0) throw err("空的多行表示");
			if (keepTail <= 0) {
				int j = v.length()-1;
				while (j >= 0 && v.list[j] == '\n') j--;

				if (j < 0) return formClip(multiline, "");

				v.setLength(j+1);

				if (keepTail == 0) v.append('\n');
			}
		} else {
			// anchor, ref, force_cast

			int prevI = i;
			while (i < in.length()) {
				if (YAML_LENDS.contains(in.charAt(i))) break;
				i++;
			}

			if (i == prevI) throw err("anchor节点必须包含名字 " + w);
			v.append(in, prevI, i);
		}

		index = i;
		return formClip(w.type(), v);
	}
}