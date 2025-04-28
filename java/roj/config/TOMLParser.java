package roj.config;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;

import static roj.config.Flags.LENIENT;
import static roj.config.Flags.ORDERED_MAP;
import static roj.config.JSONParser.*;
import static roj.config.Word.*;

/**
 * 汤小明的小巧明晰语言 <br>
 * 注释，八进制需要使用java的表示法: 0[oct...]
 *
 * @author Roj234
 * @since 2022/1/6 19:49
 */
public final class TOMLParser extends Parser {
	private static final int INLINE = 2;
	private static final short eq = 17, dot = 18, dlmb = 19, drmb = 20;

	private static final TrieTree<Word> TOML_TOKENS = new TrieTree<>();
	private static final MyBitSet TOML_LENDS = new MyBitSet();
	static {
		addKeywords(TOML_TOKENS, TRUE, "true", "false", "null");
		addSymbols(TOML_TOKENS, TOML_LENDS, lBrace, "{", "}", "[", "]", ",", "=", ".", "[[", "]]");
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
		Word w = Word.numberWord(0, v, k);
		TOML_TOKENS.put(k, w);
		TOML_TOKENS.put("+"+k, w);
		TOML_TOKENS.put("-"+k, Word.numberWord(0, v, k));
	}

	{ tokens = TOML_TOKENS; literalEnd = TOML_LENDS; }

	public TOMLParser() {}
	public TOMLParser(int flag) { super(flag); }

	@Override
	public Map<String, Integer> dynamicFlags() { return Map.of("Lenient", LENIENT, "OrderedMap", ORDERED_MAP); }
	public final ConfigMaster format() { return ConfigMaster.TOML; }

	/**
	 * @param flag LENIENT 允许修改行内表
	 */
	@Override
	CEntry element(int flag) throws ParseException {
		CMap map = comment == null ? new CMap() : new CMap.Commentable();

		CMap root = tomlObject(flag);
		if (root.size() > 0) map.put(CMap.CONFIG_TOPLEVEL, root);

		o:
		while (hasNext()) {
			Word w = next();
			switch (w.type()) {
				case lBracket:
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
	private CMap tomlObject(int flag) throws ParseException {
		Map<String, CEntry> valmap = createMap(flag);
		CMap map = (flag & INLINE) != 0 ? new IMap(valmap) : comment == null ? new CMap(valmap) : new CMap.Commentable(valmap);

		o:
		while (true) {
			firstChar = NONE_C2C;
			Word w = next();
			firstChar = SIGNED_NUMBER_C2C;

			switch (w.type()) {
				case lBracket: retractWord();
				case rBrace: break o;
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

	@SuppressWarnings("fallthrough")
	public static boolean literalSafe(CharSequence text) {
		if (ALWAYS_ESCAPE) return false;
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
				default:
					prevIndex = index = i;
					Word w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;continue;}
					return w;
				case C_NUMBER:
					prevIndex = index = i;

					if (in.length() - i > 5 && in.charAt(i + 3) == '-') {
						w = ISO8601Datetime(false);
						if (w != null) return w;
					} else if (in.length() - i > 3 && in.charAt(i + 1) == ':') {
						w = ISO8601Datetime(false);
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

		throw err("未终止的 引号 ("+end3+end3+end3+")", orig);
	}

	@Override
	protected Word onNumberFlow(CharList str, short from, short to) throws ParseException {
		if (to == DOUBLE) throw err("数之大,一个long放不下!");
		return null;
	}

	String k;

	private void dotName(String key, CMap val, int flag, String except) throws ParseException {
		firstChar = NONE_C2C;
		Word w;
		while (true) {
			w = next();
			if (w.type() != dot) break;

			Map<String, CEntry> map = val.raw();
			CEntry entry = map.get(key);
			if (entry == null) {
				Map<String, CEntry> valmap = createMap(flag);
				map.put(key, entry = comment == null ? new CMap(valmap): new CMap.Commentable(valmap));
			} else if (entry.getType() == Type.LIST) {
				CList list = entry.asList();
				if (list.size() == 0) throw err("空的行内数组");
				entry = list.get(list.size()-1);
			}
			if (entry.getType() != Type.MAP) throw err("不能覆写已存在的非表");

			key = next().val();
			val = entry.asMap();

			if ((flag & LENIENT) == 0 && val.getClass() == IMap.class) throw err("不能修改行内表");
		}

		if (!w.val().equals(except)) unexpected(w.val(), except);

		k = key;
		firstChar = SIGNED_NUMBER_C2C;

		if (comment != null && comment.length() > 0) {
			if (val.isCommentable())
				val.setComment(key, comment.toString());
			comment.clear();
		}

		if (except.equals("]]")) {
			val.getOrCreateList(key).add(tomlObject(flag));
		} else {
			CEntry entry;
			if (((flag & INLINE) != 0)) entry = tomlObject(flag & ~INLINE);
			else {
				w = next();
				entry = switch (w.type()) {
					case lBrace -> tomlObject(flag | INLINE);
					case lBracket -> JSONParser.list(this, new IList(), flag);
					case STRING, LITERAL -> {
						int i = prevIndex;
						if (next().type() == eq) {
							index = i;
							yield tomlObject(flag);
						} else {
							retractWord();
							yield CEntry.valueOf(w.val());
						}
					}
					case RFCDATE_DATE -> new CDate(w.asLong());
					case RFCDATE_DATETIME, RFCDATE_DATETIME_TZ -> new CTimestamp(w.asLong());
					case DOUBLE, FLOAT -> CEntry.valueOf(w.asDouble());
					case INTEGER -> CEntry.valueOf(w.asInt());
					case LONG -> CEntry.valueOf(w.asLong());
					case TRUE -> CBoolean.TRUE;
					case FALSE -> CBoolean.FALSE;
					case NULL -> CNull.NULL;
					default -> {
						unexpected(w.val());
						yield Helpers.nonnull();
					}
				};
			}

			CEntry me = val.raw().putIfAbsent(key, entry);
			if (me != null) {
				if (entry.getType() != me.getType()) throw err("覆盖已存在的 "+me);
				if (me.getType() != Type.MAP) throw err(me+"不是表");
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

	/**
	 * I is Inline
	 * @since 2022/1/12 13:21
	 */
	public static final class IList extends CList {
		public boolean fixed = true;
		public IList() {}
		public IList(List<CEntry> list) { super(list); }

		public CharList toTOML(CharList sb, int depth, CharSequence chain) { return super.toTOML(sb, fixed ? 3 : depth, chain); }
	}

	/**
	 * @since 2022/1/12 16:10
	 */
	public static class IMap extends CMap {
		public boolean fixed = true;
		public IMap() { super(); }
		public IMap(Map<String, CEntry> map) { super(map); }

		public CharList toTOML(CharList sb, int depth, CharSequence chain) { return super.toTOML(sb, fixed ? 3 : depth, chain); }
	}
}