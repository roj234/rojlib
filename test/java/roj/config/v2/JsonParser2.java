package roj.config.v2;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.Parser;
import roj.config.node.*;
import roj.text.ParseException;
import roj.text.Token;

import java.io.IOException;
import java.util.Map;

import static roj.text.Token.*;

/**
 * @author Roj234
 * @since 2025/09/17 11:56
 */
public class JsonParser2 extends Parser implements StreamParser2 {
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
		JSON_TOKENS.put("'", new Token().init(0, ST_LITERAL_STRING, "'"));
	}
	{ tokens = JSON_TOKENS; literalEnd = JSON_LENDS; }

	public static ConfigValue parses(CharSequence text) throws ParseException { return new roj.config.JsonParser().parse(text, 0); }

	public JsonParser2() {}
	public JsonParser2(@MagicConstant(flags = COMMENT) int commentFlag) { super(commentFlag); }

	@Override
	public ConfigValue element(@MagicConstant(flags = {NO_DUPLICATE_KEY, ORDERED_MAP, LENIENT}) int flags) throws ParseException {return element(next(), this, flags);}
	@SuppressWarnings("fallthrough")
	private static ConfigValue element(Token w, Parser wr, int flag) throws ParseException {
		switch (w.type()) {
			default: wr.unexpected(w.text()); // Always fail
			case NULL: return NullValue.NULL;
			case TRUE: return BoolValue.TRUE;
			case FALSE: return BoolValue.FALSE;
			case INTEGER: return ConfigValue.valueOf(w.asInt());
			case LONG: return ConfigValue.valueOf(w.asLong());
			case DOUBLE: return ConfigValue.valueOf(w.asDouble());
			case LITERAL, STRING: return ConfigValue.valueOf(w.text());
			case lBracket: return list(wr, new ListValue(), flag);
			case lBrace: return map(wr, flag);
		}
	}

	static ListValue list(Parser p, ListValue value, int flag) throws ParseException {
		boolean hasComma = true;

		o:
		while (true) {
			int prevIndex = p.index;
			Token w = p.next();
			switch (w.type()) {
				case rBracket: break o;
				case comma:
					if (hasComma) p.unexpected(",");
					hasComma = true;
					continue;
			}

			if (!hasComma) {
				if ((flag & LENIENT) == 0 || !onNextLine(p, prevIndex))
					p.unexpected(w.text(), "逗号");
			}
			hasComma = false;

			try {
				value.add(element(w, p, flag));
			} catch (ParseException e) {
				throw e.addPath("["+value.size()+"]");
			}
		}

		p.clearComment();
		return value;
	}

	@SuppressWarnings("fallthrough")
	static MapValue map(Parser p, int flag) throws ParseException {
		Map<String, ConfigValue> map = (flag & ORDERED_MAP) != 0 ? new LinkedHashMap<>() : new HashMap<>();
		Map<String, String> comment = null;
		boolean hasComma = true;

		o:
		while (true) {
			int prevIndex = p.index;
			Token name = p.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (hasComma) p.unexpected(",");
					hasComma = true;
					continue;
				case STRING, LITERAL: break;
				default: p.unexpected(name.text(), hasComma ? "字符串" : "逗号");
			}

			if (!hasComma) {
				if ((flag & LENIENT) == 0 || !onNextLine(p, prevIndex))
					p.unexpected(name.text(), "逗号");
			}
			hasComma = false;

			String k = name.text().intern();
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(k)) throw p.err("重复的key: "+k);

			comment = p.getComment(comment, k);

			Token w = p.next();
			if (w.type() != colon) {
				if ((flag & LENIENT) == 0) {
					p.unexpected(w.text(), ":");
				} else {
					p.retractWord();
				}
			}

			try {
				map.put(k, element(p.next(), p, flag));
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}

		p.clearComment();
		return comment == null ? new MapValue(map) : new MapValue.Commentable(map, comment);
	}

	private static boolean onNextLine(Parser parser, int index) {
		for (int i = parser.prevIndex - 1; i >= index; i--) {
			if (parser.getText().charAt(i) == '\n') return true;
		}
		return false;
	}

	//region NG API
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
		Token w = wd;
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
		Token w = wd;
		if (w.type() == NULL) return null;
		if (w.type() == Token.STRING || w.type() == Token.LITERAL) return w.text();
		throw err("Excepting string");
	}

	@Override
	public int getMap() throws ParseException {
		Token w = wd;
		if (w.type() == NULL) return -2;
		if (w.type() == lBrace) return -1;
		next();
		throw err("Excepting map start {");
	}

	@Override
	public void nextMapKey() throws ParseException {
		except(colon, "Excepting map key delim :");
		next();
	}

	@Override
	public boolean nextIsMapEnd() throws ParseException {
		Token w = next();
		if (w.type() == comma) return false;
		if (w.type() == rBrace) return true;
		throw err("Excepting map end }");
	}

	@Override
	public int getArray() throws ParseException {
		Token w = wd;
		if (w.type() == NULL) return -2;
		if (w.type() == lBracket) return -1;
		next();
		throw err("Excepting array start [");
	}

	@Override
	public boolean nextIsArrayEnd() throws ParseException {
		Token w = next();
		if (w.type() == comma) return false;
		if (w.type() == rBracket) return true;
		throw err("Excepting array end ]");
	}
	//endregion
}