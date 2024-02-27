package roj.config;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.text.Interner;
import roj.util.Helpers;

import java.util.Map;

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
	private static final MyBitSet JSON_LENDS = new MyBitSet();
	static {
		addKeywords(JSON_TOKENS, TRUE, "true", "false", "null");
		addSymbols(JSON_TOKENS, JSON_LENDS, lBrace, "{", "}", "[", "]", ",", ":");
		addWhitespace(JSON_LENDS);
		JSON_TOKENS.put("//", new Word().init(0, ST_SINGLE_LINE_COMMENT, "//"));
		JSON_TOKENS.put("/*", new Word().init(0, ST_MULTI_LINE_COMMENT, "*/"));
	}
	{ tokens = JSON_TOKENS; literalEnd = JSON_LENDS; }

	public static final int NO_DUPLICATE_KEY = 1, COMMENT = 2, ORDERED_MAP = 4;

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
		Map<String, CEntry> map = (flag & ORDERED_MAP) != 0 ? new LinkedMyHashMap<>() : new MyHashMap<>();
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
		return comment == null ? new CMap(map) : new CCommMap(map, comment);
	}

	@SuppressWarnings("fallthrough")
	private static CEntry element(Word w, Parser wr, int flag) throws ParseException {
		switch (w.type()) {
			default: wr.unexpected(w.val()); return Helpers.nonnull();
			case lBracket: return list(wr, new CList(), flag);
			case lBrace: return map(wr, flag);
			case LITERAL, STRING: return CString.valueOf(w.val());
			case NULL: return CNull.NULL;
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case INTEGER: return CInt.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case FLOAT: return CFloat.valueOf(w.asFloat());
			case DOUBLE: return CDouble.valueOf(w.asDouble());
		}
	}

	@Override
	protected final Word readStringLegacy(char key) throws ParseException { return formClip(STRING, readSlashString(key, key != '\'')); }
}