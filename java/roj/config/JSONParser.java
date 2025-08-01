package roj.config;

import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.text.Interner;

import java.util.Map;

import static roj.config.Flags.NO_DUPLICATE_KEY;
import static roj.config.Flags.ORDERED_MAP;
import static roj.config.Word.*;

/**
 * JSON5解析器
 * 另外加入了下列扩展功能：
 *   映射的键可以省略引号
 *   值（无论在何处）可以使用单引号包括，忽略其中一切转义符（除了\'）
 *
 * @author Roj234
 */
public class JSONParser extends Parser {
	static final short TRUE = 9, FALSE = 10, NULL = 11, lBrace = 12, rBrace = 13, lBracket = 14, rBracket = 15, comma = 16, colon = 17;

	private static final TrieTree<Word> JSON_TOKENS = new TrieTree<>();
	private static final BitSet JSON_LENDS = new BitSet();
	static {
		addKeywords(JSON_TOKENS, TRUE, "true", "false", "null");
		addSymbols(JSON_TOKENS, JSON_LENDS, lBrace, "{", "}", "[", "]", ",", ":");
		addWhitespace(JSON_LENDS);
		JSON_TOKENS.put("//", new Word().init(0, ST_SINGLE_LINE_COMMENT, "//"));
		JSON_TOKENS.put("/*", new Word().init(0, ST_MULTI_LINE_COMMENT, "*/"));
	}
	{ tokens = JSON_TOKENS; literalEnd = JSON_LENDS; }

	public static CEntry parses(CharSequence cs) throws ParseException { return new JSONParser().parse(cs, 0); }

	public JSONParser() {}
	public JSONParser(int commentFlag) { super(commentFlag); }

	@Override
	public CEntry element(int flag) throws ParseException { return element(next(), this, flag); }

	public Map<String, Integer> dynamicFlags() { return Map.of("NoDuplicateKey", NO_DUPLICATE_KEY, "OrderedMap", ORDERED_MAP); }
	public final ConfigMaster format() { return ConfigMaster.JSON; }

	/**
	 * 解析数组定义 <BR>
	 * [xxx, yyy, zzz] or []
	 */
	static CList list(Parser wr, CList list, int flag) throws ParseException {
		boolean more = true;

		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case rBracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
			}

			if (!more) wr.unexpected(w.val(), "逗号");
			more = false;

			try {
				list.add(element(w, wr, flag));
			} catch (ParseException e) {
				throw e.addPath("["+list.size()+"]");
			}
		}

		wr.clearComment();
		return list;
	}

	/**
	 * 解析对象定义 <BR>
	 * {xxx: yyy, zzz: uuu}
	 */
	@SuppressWarnings("fallthrough")
	static CMap map(Parser wr, int flag) throws ParseException {
		Map<String, CEntry> map = (flag & ORDERED_MAP) != 0 ? new LinkedHashMap<>() : new HashMap<>();
		Map<String, String> comment = null;
		boolean more = true;

		o:
		while (true) {
			Word name = wr.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case STRING, LITERAL: break;
				default: wr.unexpected(name.val(), more ? "字符串" : "逗号");
			}

			if (!more) wr.unexpected(name.val(), "逗号");
			more = false;

			String k = Interner.intern(name.val());
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(k)) throw wr.err("重复的key: "+k);

			comment = wr.addComment(comment, k);

			wr.except(colon, ":");
			try {
				map.put(k, element(wr.next(), wr, flag));
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}

		wr.clearComment();
		return comment == null ? new CMap(map) : new CMap.Commentable(map, comment);
	}

	@SuppressWarnings("fallthrough")
	private static CEntry element(Word w, Parser wr, int flag) throws ParseException {
		switch (w.type()) {
			default: wr.unexpected(w.val()); // Always fail
			case NULL: return CNull.NULL;
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case INTEGER: return CEntry.valueOf(w.asInt());
			case LONG: return CEntry.valueOf(w.asLong());
			case DOUBLE: return CEntry.valueOf(w.asDouble());
			case LITERAL, STRING: return CEntry.valueOf(w.val());
			case lBracket: return list(wr, new CList(), flag);
			case lBrace: return map(wr, flag);
		}
	}

	@Override
	protected final Word readStringLegacy(char key) throws ParseException { return formClip(STRING, readSlashString(key, key != '\'')); }

	/*/region NG API
	@Override
	public ParseException error(String message) {return err(message);}

	@Override
	public int peekToken() {
		return switch (wd.type()) {
			case lBracket -> TOKEN_ARRAY;
			case lBrace -> TOKEN_MAP;
			case LITERAL, STRING -> TOKEN_STRING;
			case NULL -> TOKEN_NULL;
			case TRUE, FALSE -> TOKEN_BOOL;
			case INTEGER -> TOKEN_INT;
			case LONG -> TOKEN_INT64;
			case DOUBLE -> TOKEN_FLOAT64;
			default -> -1;
		};
	}

	@Override
	public int nextToken() throws ParseException, IOException {next();return peekToken();}

	@Override
	public boolean getBoolean() throws ParseException {
		Word w = wd;
		if (w.type() == TRUE) return true;
		if (w.type() == FALSE) return false;
		throw err(w+" is not boolean");
	}

	@Override
	public int getInt() throws ParseException {return wd.asInt();}

	@Override
	public long getLong() throws ParseException {return wd.asLong();}

	@Override
	public double getDouble() throws ParseException {return wd.asDouble();}

	@Override
	public @Nullable String getString() throws ParseException {
		Word w = wd;
		if (w.type() == NULL) return null;
		if (w.type() == Word.STRING || w.type() == Word.LITERAL) return w.val();
		throw err("Excepting string");
	}

	@Override
	public int getMap() throws ParseException {
		Word w = wd;
		if (w.type() == NULL) return -2;
		if (w.type() == lBrace) return -1;
		next();
		throw err("Excepting map start {");
	}

	@Override
	public void nextMapKey() throws ParseException, IOException {
		except(colon, "Excepting map key delim :");
		next();
	}

	@Override
	public boolean nextIsMapEnd() throws ParseException, IOException {
		Word w = next();
		if (w.type() == comma) return false;
		if (w.type() == rBrace) return true;
		throw err("Excepting map end }");
	}

	@Override
	public int getArray() throws ParseException {
		Word w = wd;
		if (w.type() == NULL) return -2;
		if (w.type() == lBracket) return -1;
		next();
		throw err("Excepting array start [");
	}

	@Override
	public boolean nextIsArrayEnd() throws ParseException, IOException {
		Word w = next();
		if (w.type() == comma) return false;
		if (w.type() == rBracket) return true;
		throw err("Excepting array end ]");
	}
	//endregion*/
}