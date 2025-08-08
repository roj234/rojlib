package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;

import java.util.Map;

import static roj.config.Flags.*;
import static roj.config.Token.*;

/**
 * JSON5解析器
 * 另外加入了下列扩展功能：
 *   映射的键可以省略引号
 *   值（无论在何处）可以使用单引号包括，忽略其中一切转义符（除了\'）
 * 使用LENIENT模式时，可以使用=替代冒号，或者省略冒号和逗号，这意味着它此时可以解析HOCON-like格式和Steam的VDF格式
 *
 * @author Roj234
 */
public class JSONParser extends Parser {
	static final short TRUE = 9, FALSE = 10, NULL = 11, lBrace = 12, rBrace = 13, lBracket = 14, rBracket = 15, comma = 16, colon = 17;

	private static final TrieTree<Token> JSON_TOKENS = new TrieTree<>();
	private static final BitSet JSON_LENDS = new BitSet();
	static {
		addKeywords(JSON_TOKENS, TRUE, "true", "false", "null");
		addSymbols(JSON_TOKENS, JSON_LENDS, lBrace, "{", "}", "[", "]", ",", ":");
		JSON_TOKENS.put("=", new Token().init(colon, 0, "="));
		JSON_LENDS.add('=');
		addWhitespace(JSON_LENDS);
		JSON_TOKENS.put("//", new Token().init(0, ST_SINGLE_LINE_COMMENT, "//"));
		JSON_TOKENS.put("/*", new Token().init(0, ST_MULTI_LINE_COMMENT, "*/"));
	}
	{ tokens = JSON_TOKENS; literalEnd = JSON_LENDS; }

	public static CEntry parses(CharSequence cs) throws ParseException { return new JSONParser().parse(cs, 0); }

	public JSONParser() {}
	public JSONParser(@MagicConstant(flags = COMMENT) int commentFlag) { super(commentFlag); }

	@Override
	public CEntry element(@MagicConstant(flags = {NO_DUPLICATE_KEY, ORDERED_MAP, LENIENT}) int flag) throws ParseException { return element(next(), this, flag); }

	/**
	 * 解析数组定义 <BR>
	 * [xxx, yyy, zzz] or []
	 */
	static CList list(Parser wr, CList list, int flag) throws ParseException {
		boolean hasComma = true;

		o:
		while (true) {
			int prevIndex = wr.index;
			Token w = wr.next();
			switch (w.type()) {
				case rBracket: break o;
				case comma:
					if (hasComma) wr.unexpected(",");
					hasComma = true;
					continue;
			}

			if (!hasComma) {
				if ((flag & LENIENT) == 0 || !checkCRLF(wr, prevIndex))
					wr.unexpected(w.text(), "逗号");
			}
			hasComma = false;

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
		boolean hasComma = true;

		o:
		while (true) {
			int prevIndex = wr.index;
			Token name = wr.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (hasComma) wr.unexpected(",");
					hasComma = true;
					continue;
				case STRING, LITERAL: break;
				default: wr.unexpected(name.text(), hasComma ? "字符串" : "逗号");
			}

			if (!hasComma) {
				if ((flag & LENIENT) == 0 || !checkCRLF(wr, prevIndex))
					wr.unexpected(name.text(), "逗号");
			}
			hasComma = false;

			String k = name.text().intern();
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(k)) throw wr.err("重复的key: "+k);

			comment = wr.addComment(comment, k);

			Token w = wr.next();
			if (w.type() != colon) {
				if ((flag & LENIENT) == 0) {
					wr.unexpected(w.text(), ":");
				} else {
					wr.retractWord();
				}
			}

			try {
				map.put(k, element(wr.next(), wr, flag));
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}

		wr.clearComment();
		return comment == null ? new CMap(map) : new CMap.Commentable(map, comment);
	}

	private static boolean checkCRLF(Parser parser, int index) {
		for (int i = parser.prevIndex - 1; i >= index; i--) {
			if (parser.getText().charAt(i) == '\n') return true;
		}
		return false;
	}

	@SuppressWarnings("fallthrough")
	private static CEntry element(Token w, Parser wr, int flag) throws ParseException {
		switch (w.type()) {
			default: wr.unexpected(w.text()); // Always fail
			case NULL: return CNull.NULL;
			case TRUE: return CBoolean.TRUE;
			case FALSE: return CBoolean.FALSE;
			case INTEGER: return CEntry.valueOf(w.asInt());
			case LONG: return CEntry.valueOf(w.asLong());
			case DOUBLE: return CEntry.valueOf(w.asDouble());
			case LITERAL, STRING: return CEntry.valueOf(w.text());
			case lBracket: return list(wr, new CList(), flag);
			case lBrace: return map(wr, flag);
		}
	}

	@Override
	protected final Token readStringLegacy(char key) throws ParseException { return formClip(STRING, readSlashString(key, key != '\'')); }

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