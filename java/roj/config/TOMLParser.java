package roj.config;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static roj.config.JSONParser.*;
import static roj.config.word.Word.*;

/**
 * 汤小明的小巧明晰语言 <br>
 * 注释，八进制需要使用java的表示法: 0[oct...]
 *
 * @author Roj234
 * @since 2022/1/6 19:49
 */
public final class TOMLParser extends Parser<CMapping> {
	public static final int LENIENT = 1;
	private static final int INLINE = 2;
	private static final short eq = 18, dot = 19, dlmb = 20, drmb = 21;

	private static final TrieTree<Word> TOML_TOKENS = new TrieTree<>();
	private static final MyBitSet TOML_LENDS = new MyBitSet();
	static {
		addKeywords(TOML_TOKENS, TRUE, "true", "false", "null");
		addSymbols(TOML_TOKENS, TOML_LENDS, left_l_bracket, "{", "}", "[", "]", ",", "=", ".", "[[", "]]");
		addWhitespace(TOML_LENDS);

		num("nan", Double.NaN);
		num("inf", Double.POSITIVE_INFINITY);

		TOML_TOKENS.put("#", new Word().init(0, ST_SINGLE_LINE_COMMENT, "#"));
		TOML_TOKENS.put("'''", new Word().init(0, -1, "'''"));
		TOML_TOKENS.put("\"\"\"", new Word().init(0, -1, "\"\"\""));
		TOML_TOKENS.put("'", new Word().init(0, ST_LITERAL_STRING, "'"));
		TOML_TOKENS.put("\"", new Word().init(0, ST_STRING, "\""));
		TOML_LENDS.addAll("+-#\"'");
	}
	private static void num(String k, double v) {
		D pos = new D(0, v, k);
		TOML_TOKENS.put(k, pos);
		TOML_TOKENS.put("+"+k, pos);
		TOML_TOKENS.put("-"+k, new D(0, v, k));
	}

	{ tokens = TOML_TOKENS; literalEnd = TOML_LENDS; }

	public static void main(String[] args) throws ParseException, IOException {
		System.out.print(parses(IOUtil.readUTF(new File(args[0]))).toTOML());
	}

	public static CMapping parses(CharSequence string) throws ParseException {
		return new TOMLParser().parse(string, 0);
	}
	public static CMapping parses(CharSequence string, int flag) throws ParseException {
		return new TOMLParser().parse(string, flag);
	}

	public TOMLParser() {}
	public TOMLParser(int flag) { super(flag); }

	@Override
	public final CMapping parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);

		CMapping entry;
		try {
			entry = parse0(flags);
			if ((flags & NO_EOF) == 0) except(EOF, "EOF");
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}

		return entry;
	}

	public final int availableFlags() { return LENIENT; }
	public final String format() { return "TOML"; }

	@Override
	public final CharList append(CEntry entry, int flag, CharList sb) {
		return entry.appendTOML(sb, new CharList());
	}

	/**
	 * @param flag LENIENT 允许修改行内表
	 */
	public CMapping parse0(int flag) throws ParseException {
		CMapping map = comment == null ? new CMapping() : new CCommMap();

		CMapping root = tomlObject(flag);
		if (root.size() > 0) map.put(CMapping.CONFIG_TOPLEVEL, root);

		o:
		while (hasNext()) {
			Word w = next();
			switch (w.type()) {
				case left_m_bracket:
					w = next();
					if (w.type() != LITERAL && w.type() != STRING) unexpected(w.val(), "键");
					try {
						dotName(w.val(), map, flag | INLINE, "]");
					} catch (ParseException e) {
						throw e.addPath('.' + k);
					}
					break;
				case dlmb:
					w = next();
					if (w.type() != LITERAL && w.type() != STRING) unexpected(w.val(), "键");
					try {
						dotName(w.val(), map, flag, "]]");
					} catch (ParseException e) {
						throw e.addPath('.' + k);
					}
					break;
				case EOF: break o;
				default: unexpected(w.val(), "[ 或 [[");
			}
		}

		return map;
	}

	/**
	 * 解析对象定义 <BR>
	 * {xxx = yyy, zzz = uuu}
	 */
	@SuppressWarnings("fallthrough")
	private CMapping tomlObject(int flag) throws ParseException {
		Map<String, CEntry> valmap = createMap(flag);
		CMapping map = (flag & INLINE) != 0 ? new CTOMLFxMap(valmap) : comment == null ? new CMapping(valmap) : new CCommMap(valmap);

		o:
		while (true) {
			firstChar = NONE_C2C;
			Word w = next();
			firstChar = SIGNED_NUMBER_C2C;

			switch (w.type()) {
				case left_m_bracket: retractWord();
				case right_l_bracket: break o;
				case INTEGER: case LITERAL: case STRING: break;
				default:
					if ((flag & INLINE) == 0) {
						retractWord();
						break o;
					}
					unexpected(w.val(), "字符串");
			}

			try {
				dotName(w.val(), map, flag & ~INLINE, "=");
			} catch (ParseException e) {
				throw e.addPath(k + '.');
			}
			if ((flag & (INLINE | LENIENT)) == 0) ensureLineEnd();
		}

		return map;
	}

	private static Map<String, CEntry> createMap(int flag) { return (flag & ORDERED_MAP) != 0 ? new LinkedMyHashMap<>() : new MyHashMap<>(); }

	CEntry element(int flag) throws ParseException {
		Word w = next();
		switch (w.type()) {
			case left_l_bracket: return tomlObject(flag | INLINE);
			case left_m_bracket: return JSONParser.list(this, new CTOMLList(), flag);
			case STRING:
			case LITERAL: {
				int i = prevIndex;
				if (next().type() == eq) {
					index = i;
					return tomlObject(flag);
				} else {
					retractWord();
					return CString.valueOf(w.val());
				}
			}
			case RFCDATE_DATE: return new CDate(w.asLong());
			case RFCDATE_DATETIME:
			case RFCDATE_DATETIME_TZ: return new CTimestamp(w.asLong());
			case DOUBLE:
			case FLOAT: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInteger.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case NULL: return CNull.NULL;
			default: unexpected(w.val()); return Helpers.nonnull();
		}
	}

	@SuppressWarnings("fallthrough")
	public static boolean literalSafe(CharSequence text) {
		if (LITERAL_UNSAFE) return false;
		if (text.length() == 0) return false;

		for (int i = 0; i < text.length(); i++) {
			if (TOML_LENDS.contains(text.charAt(i))) return false;
		}
		return true;
	}

	@SuppressWarnings("fallthrough")
	@Override
	public Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (firstChar.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i))) {
						prevIndex = index = i;
						return readDigit(true);
					}
					// fall to literal(symbol)
				default: prevIndex = index = i; return readSymbol();
				case C_NUMBER:
					prevIndex = index = i;

					if (in.length() - i > 5 && in.charAt(i + 3) == '-') {
						Word w = ISO8601Datetime(true);
						if (w != null) return w;
					} else if (in.length() - i > 3 && in.charAt(i + 1) == ':') {
						Word w = ISO8601Datetime(true);
						if (w != null) return w;
					}

					return readDigit(false);
				case C_WHITESPACE: i++;
			}
		}

		index = i;
		return eof();
	}

	@Override
	protected Word onSpecialToken(Word w) throws ParseException { return formClip(STRING, readMultiLine(w.val().charAt(0))); }
	private String readMultiLine(char end3) throws ParseException {
		CharSequence in = input;
		int i = index;

		//if (remain() < 3) throw err("EOF");

		CharList v = found; v.clear();

		while (i < in.length()) {
			char c = in.charAt(i);
			if (c != '\r' && c != '\n') break;
			i++;
		}

		int end3Amount = 0;
		boolean slash = false;
		boolean skip = false;

		while (i < in.length()) {
			char c = in.charAt(i++);
			if (slash) {
				i = _removeSlash(input, c, v, i);
				slash = false;
			} else {
				if (end3 == c) {
					if (++end3Amount == 3) {
						index = i;

						v.setLength(v.length()-3);
						return v.toString();
					}

					v.append(c);
				} else {
					end3Amount = 0;
					if (c == '\\' && end3 == '"') {
						if (in.charAt(i) == '\r' || in.charAt(i) == '\n') {
							skip = true;
						} else {
							slash = true;
						}
					} else if (!skip || !WHITESPACE.contains(c)) {
						skip = false;
						v.append(c);
					}
				}
			}
		}

		int orig = index;
		index = i;

		throw err("未终止的 引号 (" + end3 + end3 + end3 + ")", orig);
	}

	@Override
	protected void onNumberFlow(CharSequence str, short from, short to) throws ParseException {
		if (to == DOUBLE) throw err("数之大,一个long放不下!");
	}

	String k;

	private void dotName(String key, CMapping val, int flag, String except) throws ParseException {
		firstChar = NONE_C2C;
		Word w;
		while (true) {
			w = next();
			if (w.type() != dot) break;

			Map<String, CEntry> map = val.raw();
			CEntry entry = map.get(key);
			if (entry == null) {
				Map<String, CEntry> valmap = createMap(flag);
				map.put(key, entry = comment == null ? new CMapping(valmap): new CCommMap(valmap));
			} else if (entry.getType() == Type.LIST) {
				CList list = entry.asList();
				if (list.size() == 0) throw err("空的行内数组");
				entry = list.get(list.size()-1);
			}
			if (entry.getType() != Type.MAP) throw err("不能覆写已存在的非表");

			key = next().val();
			val = entry.asMap();

			if ((flag & LENIENT) == 0 && val.getClass() == CTOMLFxMap.class) throw err("不能修改行内表");
		}

		if (!w.val().equals(except)) unexpected(w.val(), except);

		k = key;
		firstChar = SIGNED_NUMBER_C2C;

		if (comment != null && comment.length() > 0) {
			if (val.isCommentSupported())
				val.putComment(key, comment.toString());
			comment.clear();
		}

		if (except.equals("]]")) {
			val.getOrCreateList(key).add(tomlObject(flag));
		} else {
			CEntry entry = ((flag & INLINE) != 0) ? tomlObject(flag & ~INLINE) : element(flag);

			CEntry me = val.raw().putIfAbsent(key, entry);
			if (me != null) {
				if (entry.getType() != me.getType()) throw err("覆盖已存在的 "+me.toShortJSONb());
				if (me.getType() != Type.MAP) throw err(me.toShortJSONb()+"不是表");
				me.asMap().merge(entry.asMap(), false, true);
			}
		}

		clearComment();
	}

	private void ensureLineEnd() throws ParseException {
		CharSequence in = this.input;
		int i = this.index;
		while (i < in.length()) {
			char c = in.charAt(i);
			if (c == '\r' || c == '\n') {
				break;
			}
			if (!WHITESPACE.contains(c)) throw err("我们建议你换个行");
		}
		index = i;
	}
}