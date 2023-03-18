package roj.config;

import roj.collect.*;
import roj.config.data.*;
import roj.config.serial.ToJson;
import roj.config.word.Word;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.Map;

import static roj.config.word.Word.*;

/**
 * JSON解析器
 *
 * @author Roj234
 */
public class JSONParser extends Parser<CEntry> {
	static final short TRUE = 10, FALSE = 11, NULL = 12, left_l_bracket = 13, right_l_bracket = 14, left_m_bracket = 15, right_m_bracket = 16, comma = 17, colon = 18;

	private static final TrieTree<Word> JSON_TOKENS = new TrieTree<>();
	private static final MyBitSet JSON_LENDS = new MyBitSet();
	private static final Int2IntMap JSON_C2C = new Int2IntMap();
	static {
		addKeywords(JSON_TOKENS, 10, "true", "false", "null");
		addSymbols(JSON_TOKENS, JSON_LENDS, 13, "{", "}", "[", "]", ",", ":");
		addWhitespace(JSON_LENDS);
		fcSetDefault(JSON_C2C, 7);
		JSON_C2C.put('\'', C_SYH);
	}
	{ tokens = JSON_TOKENS; literalEnd = JSON_LENDS; firstChar = JSON_C2C; }

	public static final int
		NO_DUPLICATE_KEY = 1,
		LITERAL_KEY = 2,
		UNESCAPED_SINGLE_QUOTE = 4,
		NO_EOF = 8,
		INTERN = 16,
		LENIENT_COMMA = 32,
		COMMENT = 64,
		ORDERED_MAP = 128;

	public static CEntry parses(CharSequence cs) throws ParseException {
		return new JSONParser().parse(cs, 0);
	}
	public static CEntry parses(CharSequence cs, int flag) throws ParseException {
		return new JSONParser(flag).parse(cs, flag);
	}

	public JSONParser() {}
	public JSONParser(int solidFlag) {
		super(solidFlag);
	}

	@Override
	public CEntry element(int flag) throws ParseException { return element(this, flag); }

	public final int availableFlags() { return NO_DUPLICATE_KEY|LITERAL_KEY|UNESCAPED_SINGLE_QUOTE|NO_EOF|LENIENT_COMMA|ORDERED_MAP; }
	public final String format() { return "JSON"; }

	public final CharList append(CEntry entry, int flag, CharList sb) {
		ToJson ser = new ToJson();
		if (flag != 0) ser.tabIndent();
		entry.forEachChild(ser.sb(sb));
		return sb;
	}

	/**
	 * 解析数组定义 <BR>
	 * [xxx, yyy, zzz] or []
	 */
	static CList list(Parser<?> wr, CList list, int flag) throws ParseException {
		boolean more = true;

		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case right_m_bracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				default: wr.retractWord();
			}

			if (!more && (flag & LENIENT_COMMA) == 0) wr.unexpected(w.val(), "逗号");
			more = false;

			try {
				list.add(element(wr, flag));
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
	static CMapping map(Parser<?> wr, int flag) throws ParseException {
		Map<String, CEntry> map = (flag & ORDERED_MAP) != 0 ? new LinkedMyHashMap<>() : new MyHashMap<>();
		Map<String, String> comment = null;
		boolean more = true;

		o:
		while (true) {
			Word name = wr.next();
			switch (name.type()) {
				case right_l_bracket: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case STRING: break;
				case LITERAL: if ((flag & LITERAL_KEY) != 0) break;
				default: wr.unexpected(name.val(), more ? "字符串" : "逗号");
			}

			if (!more && (flag & LENIENT_COMMA) == 0) wr.unexpected(name.val(), "逗号");
			more = false;

			String v = name.val();
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(v)) throw wr.err("重复的key: " + v);

			comment = wr.addComment(comment, v);

			wr.except(colon, ":");
			try {
				map.put(v, element(wr, flag));
			} catch (ParseException e) {
				throw e.addPath('.'+v);
			}
		}

		wr.clearComment();
		return comment == null ? new CMapping(map) : new CCommMap(map, comment);
	}

	private static CEntry element(Parser<?> wr, int flag) throws ParseException {
		Word w = wr.next();
		switch (w.type()) {
			case left_m_bracket: return list(wr, new CList(), flag);
			case STRING: return CString.valueOf(w.val());
			case DOUBLE:
			case FLOAT: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInteger.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case NULL: return CNull.NULL;
			case left_l_bracket: return map(wr, flag);
			case LITERAL: if ((flag & LITERAL_KEY) != 0) return CString.valueOf(w.val());
			default: wr.unexpected(w.val()); return Helpers.nonnull();
		}
	}

	@Override
	protected final Word readConstString(char key) throws ParseException {
		return formClip(STRING, readSlashString(key, key != '\'' || (flag & UNESCAPED_SINGLE_QUOTE) == 0));
	}
}
