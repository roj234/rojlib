package roj.config;

import roj.collect.*;
import roj.config.data.*;
import roj.config.serial.ToYaml;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Map;

import static roj.config.JSONParser.*;
import static roj.config.word.Word.EOF;
import static roj.config.word.Word.STRING;

/**
 * Yaml解析器
 *
 * @author Roj234
 * @since 2021/7/7 2:03
 */
public class YAMLParser extends Parser<CEntry> {
	public static final int LENIENT = 4;

	static final short delim = 19,
		ask = 20,  join = 21,
		anchor = 22, ref = 23, force_cast = 24,
		multiline = 25, multiline_clump = 26;

	private static final TrieTree<Word> YAML_TOKENS = new TrieTree<>();
	// - for timestamp
	private static final MyBitSet YAML_LENDS = new MyBitSet(), YAML_LENDS_NUM = MyBitSet.from("\r\n"), TMP1 = MyBitSet.from("\r\n:");
	private static final Int2IntMap YAML_C2C = new Int2IntMap(), YAML_C2C_NN = new Int2IntMap();

	static {
		put(10, TRUE, "True", "On", "Yes");
		put(11, FALSE, "False", "Off", "No");
		put(12, NULL, "Null");
		addSymbols(YAML_TOKENS, YAML_LENDS, 13, "{","}","[","]",",",":","-","?","<<","&","*","!!","|",">");
		addWhitespace(YAML_LENDS);
		markSpecial(YAML_TOKENS,"&","*","!!","|",">");

		fcSetDefault(YAML_C2C_NN, 0);
		YAML_C2C_NN.put('#', C_COMMENT);

		YAML_C2C.putAll(YAML_C2C_NN);
		fcSetDefault(YAML_C2C, 3);
	}

	private static void put(int begin, int id, String... tokens) {
		for (String token : tokens) {
			Word w = new Word().init(id, 0, token);
			YAML_TOKENS.put(token, w);
			YAML_TOKENS.put(token.toLowerCase(), w);
			YAML_TOKENS.put(token.toUpperCase(), w);
		}
	}
	{ tokens = YAML_TOKENS; literalEnd = YAML_LENDS; firstChar = YAML_C2C; }

	public static CEntry parses(CharSequence string) throws ParseException {
		return new YAMLParser().parse(string, 0);
	}
	public static CEntry parses(CharSequence string, int flag) throws ParseException {
		return new YAMLParser().parse(string, flag);
	}

	public YAMLParser() {}
	public YAMLParser(int flag) { super(flag); }

	public final int availableFlags() { return ORDERED_MAP | LENIENT_COMMA | LENIENT | NO_DUPLICATE_KEY | NO_EOF; }
	public final String format() { return "YAML"; }

	@Override
	public final CharList append(CEntry entry, int flag, CharList sb) {
		ToYaml ser = new ToYaml();
		entry.forEachChild(ser.sb(sb));
		return sb;
	}

	/**
	 * 解析行式数组定义 <BR>
	 * - xx : yy
	 * - cmw :
	 * - xyz
	 */
	private CList yamlLineArray() throws ParseException {
		CList list = new CList();

		int superIndent = prevIndent;
		int firstIndent = getIndent();
		if (firstIndent <= superIndent) throw err("下级缩进("+firstIndent+")<=上级("+superIndent+")");

		while (true) {
			int line = LN;
			Word w = next();
			if (LN > line && getIndent() == firstIndent && w.type() == delim) {
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

			int off = getIndent();
			if (w.type() != delim || off < firstIndent) {
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
		int firstIndent = getIndent();
		if (firstIndent <= superIndent) throw err("下级缩进("+firstIndent+")<=上级("+superIndent+")");

		retractWord();
		cyl:
		while (true) {
			String name = w.val();
			switch (w.type()) {
				case ask: throw err("配置文件不用字符串做key是坏文明");
				case join:
					// <<: *xxx
					// 固定搭配
					except(colon, ":");
					except(ref, "*");
					CEntry entry = anchors.get(w.val());
					if (entry == null) throw err("不存在的锚点 " + w.val());
					if (!entry.getType().isSimilar(Type.MAP)) throw err("锚点 " + w.val() + " 无法转换为map");
					map.putAll(entry.asMap().raw());
					break;
				case Word.LITERAL:
				case Word.STRING:
				case Word.INTEGER:
				case Word.LONG:
				case Word.DOUBLE:
				case Word.FLOAT:
				case NULL:
					if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: " + name);

					except(colon, ":");
					comment = addComment(comment, name);

					try {
						prevIndent = firstIndent;
						map.put(name, element(flag));
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

			int indent = getIndent();
			if (indent < firstIndent) {
				// 上一个是List
				if (firstIndent == Integer.MAX_VALUE) {
					firstIndent = indent;
					continue;
				}

				retractWord();
				break;
			} else if (indent != firstIndent) throw err("缩进有误:"+indent+"/"+firstIndent);
		}

		clearComment();
		return comment == null ? new CMapping(map) : new CCommMap(map, comment);
	}

	final CEntry element(int flag) throws ParseException {
		Word w = next();
		String cnt = w.val();
		switch (w.type()) {
			case force_cast: {
				CEntry val = element(flag);
				switch (cnt) {
					case "str": return CString.valueOf(val.asString());
					case "float": return CDouble.valueOf(val.asDouble());
					case "int": return CInteger.valueOf(val.asInteger());
					case "bool": return CBoolean.valueOf(val.asBool());
					case "long": return CLong.valueOf(val.asLong());
					case "map": return val.asMap();
					case "set":
						CMapping map = val.asMap();
						for (CEntry entry1 : map.values()) {
							if (entry1 != CNull.NULL) throw err("无法转换为set: 值不是null: " + entry1.toShortJSON());
						}
						return map;
					default: throw err("我不知道你要转换成啥, 支持 str float int bool map set: " + cnt);
				}
			}
			case left_m_bracket:
				this.flag |= LITERAL_KEY;
				try {
					return JSONParser.list(this, new CList(), flag|LITERAL_KEY);
				} finally {
					this.flag ^= LITERAL_KEY;
				}
			case left_l_bracket:
				this.flag |= LITERAL_KEY;
				try {
					return JSONParser.map(this, flag|LITERAL_KEY);
				} finally {
					this.flag ^= LITERAL_KEY;
				}
			case multiline: case multiline_clump: return CString.valueOf(cnt);
			case Word.STRING:
			case Word.LITERAL: {
				CEntry map = checkMap();
				return map != null ? map : CString.valueOf(cnt);
			}
			case Word.DOUBLE:
			case Word.FLOAT: {
				double number = w.asDouble();
				CEntry map = checkMap();
				return map != null ? map : CDouble.valueOf(number);
			}
			case Word.INTEGER: {
				int number = w.asInt();
				CEntry map = checkMap();
				return map != null ? map : CInteger.valueOf(number);
			}
			case Word.RFCDATE_DATE: return new CDate(w.asLong());
			case Word.RFCDATE_DATETIME:
			case Word.RFCDATE_DATETIME_TZ: return new CTimestamp(w.asLong());
			case Word.LONG: return CLong.valueOf(w.asLong());
			case TRUE: case FALSE: {
				boolean b = w.type() == TRUE;
				CEntry map = checkMap();
				return map != null ? map : CBoolean.valueOf(b);
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
				if (prevLN == LN && LN != 1) throw err("期待换行");
				return yamlLineArray();
			case anchor: {
				CEntry val = element(flag);
				anchors.put(cnt, val);
				return val;
			}
			case ref: {
				CEntry val = anchors.get(cnt);
				if (val == null) throw err("不存在的锚点 " + cnt);
				return val;
			}
			case Word.EOF: return CNull.NULL;
			default: unexpected(cnt); return Helpers.nonnull();
		}
	}

	final Word tmpKey = new Word();
	private CEntry checkMap() throws ParseException {
		int i = prevIndex;
		Word firstKey = tmpKey.init(wd.type(), wd.pos(), wd.val());

		int ln = prevLN;
		short type = nextNN().type();

		if (type == colon) {
			if (getIndent() <= prevIndent) {
				if (prevLN == ln) {
					throw err("若"+firstKey.val()+"是一个key,则其必须换行");
				} else {
					index = i;
					return CNull.NULL;
				}
			}

			return yamlObject(firstKey);
		}

		retractWord();
		return null;
	}

	// -1:true 0:false 1:ln 2:unchecked
	@SuppressWarnings("fallthrough")
	public static int literalSafe(CharSequence text) {
		if (LITERAL_UNSAFE) return 0;

		if (text.length() == 0) return 0;

		char c = text.charAt(0);
		// BTW? ref: https://verytoolz.com/yaml-formatter.html
		if (c == '!') return 2;

		if (c == '\'' || c == '"') return 2;

		if (WHITESPACE.contains(text.charAt(text.length()-1)) || WHITESPACE.contains(c)) return 2;

		if (YAML_TOKENS.startsWith_R(text)) {
			if (YAML_LENDS.contains(c) || YAML_TOKENS.containsKey(text)) // trueN, falseN, nullX
				return 0;
		}
		if (TextUtil.isNumber(text) >= 0) return 0;

		for (int i = 1; i < text.length(); i++) {
			c = text.charAt(i);
			if (c == '\n') return 1;
			if (i+1 >= text.length() || WHITESPACE.contains(text.charAt(i+1))) {
				if (c == ':' || c == '-') {
					return 0;
				}
			}
		}

		return -1;
	}

	final MyHashMap<String, CEntry> anchors = new MyHashMap<>();
	int prevIndent;

	@Override
	public final YAMLParser init(CharSequence seq) {
		anchors.clear();
		prevIndent = -1;
		indentPos = -1;
		aFlag |= COMPUTE_LINES;
		super.init(seq);
		return this;
	}
	@Override
	@SuppressWarnings("fallthrough")
	public final Word readWord() throws ParseException {
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
							index = i-1;
							return readDigit(true);
						}
						// fall to literal(symbol)
					default:
					case C_DEFAULT: index = i-1; return readSymbol();
					case C_NUMBER:
						index = i-1;
						return readDigit(false);
					case C_COMMENT:
						index = i;
						singleLineComment(null);
						i = index;
						break;
					case C_DYH:
						index = i;

						CharSequence val;
						if (in.charAt(i) == c) {
							if (i+1 < in.length() && in.charAt(i+1) == c) {
								index = i+2;
								multiLineComment(comment, "'''"); // '''
								i = index;
								break;
							} else {
								val = "";
							}
						} else {
							val = readSlashString(c, false);
						}
						return formClip(STRING, val);
					case C_SYH:
						index = i;
						return formClip(STRING, readSlashString(c, true));
				}
			}

			index = i;
			return eof();
		} finally {
			checkLine();
		}
	}

	protected final Word nextNN() throws ParseException {
		firstChar = YAML_C2C_NN;
		Word w = next();
		firstChar = YAML_C2C;
		return w;
	}

	@SuppressWarnings("fallthrough")
	@Override
	protected final boolean isValidCombo(int off, Word word) {
		if ((flag & LITERAL_KEY) != 0) return true;

		switch (word.type()) {
			case TRUE: case FALSE: case NULL:
				if (!whiteSpaceUntilNextLine(index+off)) return false;
				break;
			case delim: if (wd.type() == delim) return onNextLine(index+off);
			case colon:
				// :和-后面必须是WHITESPACE
				if (!WHITESPACE.contains(lookAhead(off))) return false;
				break;
			case ask: case join:
				if (firstChar == YAML_C2C) return false;
				break;
		}
		return super.isValidCombo(off, word);
	}
	private boolean onNextLine(int len) {
		CharSequence in = input;
		int i = prevIndex;
		while (i < len) {
			if (in.charAt(i++) == '\n') return true;
		}
		return false;
	}
	private boolean whiteSpaceUntilNextLine(int i) {
		CharSequence in = input;
		while (i < in.length()) {
			char c = in.charAt(i++);
			if (!WHITESPACE.contains(c)) return false;
			if (c == '\r' || c == '\n') return true;
		}
		return true;
	}

	@Override
	protected final Word readDigit(boolean sign) throws ParseException {
		if ((flag & LITERAL_KEY) != 0) return digitReader(sign, DIGIT_HBO);

		literalEnd = YAML_LENDS_NUM;
		try {
			return digitReader(sign, DIGIT_HBO);
		} finally {
			literalEnd = YAML_LENDS;
		}
	}
	@Override
	protected final Word onInvalidNumber(char value, int i, String reason) throws ParseException {
		if (lookAhead(4) == '-') {
			Word w = ISO8601Datetime(false);
			if (w != null) return w;
		}
		return readLiteral();
	}

	protected final Word readLiteral() throws ParseException {
		// {a:b}
		if ((flag & LITERAL_KEY) != 0) return super.readLiteral();

		CharSequence in = input;
		int i = index;

		int prevI = i, textI = i;

		while (i < in.length()) {
			char c = in.charAt(i);
			if (TMP1.contains(c)) {
				if (c == '\r' || c == '\n') break;

				if (c == ':') {
					if (i + 1 >= in.length() || WHITESPACE.contains(in.charAt(i + 1))) {
						break;
					}
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

		return formLiteralClip(v);
	}

	@Override
	protected final Word _formClip(Word w) throws ParseException {
		CharSequence in = input;
		int i = index;
		CharList v = found; v.clear();

		/**
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

			// 严格条件限制下必须大于prevIndent
			int min = (flag&LENIENT) == 0 ? prevIndent : 0;
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
			while (true) {
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

					if ((clump&1) != 0) {
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

	int indent, indentPos = -1;
	final int getIndent() {
		if (index == indentPos) return indent;

		CharSequence in = input;
		int count = 0;
		int i = indentPos = index;
		while (i > 0) {
			char c = in.charAt(--i);
			switch (c) {
				case '\'': case '"': i = escapePrev(c,i); break;
				case '\r': case '\n': return indent = count;
				case '-': if (count > 0) return indent = Integer.MAX_VALUE;
				break;
				case ' ': case '\t': count++; break;
				default: count = 0; break;
			}
		}

		indent = 0;
		return 0;
	}

	private int escapePrev(char c, int i) {
		CharSequence in = input;
		while (i > 0) {
			char c1 = in.charAt(--i);
			if (c1 == c && (i==0 || in.charAt(i-1) != '\\')) break;
		}
		return i;
	}
}
