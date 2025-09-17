package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.node.*;
import roj.text.ParseException;
import roj.text.Token;

import java.util.Map;

import static roj.text.Token.*;

/**
 * JSON5解析器
 * 另外加入了下列扩展功能：
 *   映射的键可以省略引号
 *   值（无论在何处）可以使用单引号包括，忽略其中一切转义符（除了\'）
 * 使用LENIENT模式时，可以使用=替代冒号，或者省略冒号和逗号，这意味着它此时可以解析HOCON-like格式和Steam的VDF格式
 *
 * @author Roj234
 */
public class JsonParser extends Parser implements StreamParser {
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

	public static ConfigValue parses(CharSequence text) throws ParseException { return new JsonParser().parse(text, 0); }

	public JsonParser() {}
	public JsonParser(@MagicConstant(flags = COMMENT) int commentFlag) { super(commentFlag); }

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

	@Override
	public void parse(CharSequence text, @MagicConstant(flags = {LENIENT}) int flag, ValueEmitter emitter) throws ParseException {
		init(text);
		try {
			streamElement(flag, emitter);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			input = null;
		}
	}
	@SuppressWarnings("fallthrough")
	public void streamElement(@MagicConstant(flags = {LENIENT}) int flags, ValueEmitter emitter) throws ParseException {
		Token w = next();
		try {
			switch (w.type()) {
				default -> unexpected(w.text());
				case lBracket -> list(this, emitter, flags);
				case lBrace -> map(this, emitter, flags);
				case LITERAL, STRING -> emitter.emit(w.text());
				case NULL -> emitter.emitNull();
				case TRUE -> emitter.emit(true);
				case FALSE -> emitter.emit(false);
				case INTEGER -> emitter.emit(w.asInt());
				case LONG -> emitter.emit(w.asLong());
				case DOUBLE -> emitter.emit(w.asDouble());
			}
		} catch (Exception e) {
			if (e instanceof ParseException) throw e;
			else throw adaptError(this,e);
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
	static <P extends Parser&StreamParser> void list(P p, ValueEmitter emitter, int flag) throws ParseException {
		boolean hasComma = true;
		int index = 0;

		emitter.emitList();
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

			if (p.comment != null && p.comment.length() != 0) {
				emitter.comment(p.comment.toString());
				p.comment.clear();
			}

			try {
				p.streamElement(flag, emitter);
			} catch (ParseException e) {
				throw e.addPath("["+index+"]");
			}
			index++;
		}

		p.clearComment();
		emitter.pop();
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
	@SuppressWarnings("fallthrough")
	static <T extends Parser&StreamParser> void map(T p, ValueEmitter emitter, int flag) throws ParseException {
		boolean hasComma = true;

		emitter.emitMap();
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

			String k = name.text();

			if (p.comment != null && p.comment.length() != 0) {
				emitter.comment(p.comment.toString());
				p.comment.clear();
			}

			Token w = p.next();
			if (w.type() != colon) {
				if ((flag & LENIENT) == 0) {
					p.unexpected(w.text(), ":");
				} else {
					p.retractWord();
				}
			}

			emitter.emitKey(k);
			try {
				p.streamElement(flag, emitter);
			} catch (ParseException e) {
				throw e.addPath('.'+k);
			}
		}

		p.clearComment();
		emitter.pop();
	}

	private static boolean onNextLine(Parser parser, int index) {
		for (int i = parser.prevIndex - 1; i >= index; i--) {
			if (parser.getText().charAt(i) == '\n') return true;
		}
		return false;
	}

	static ParseException adaptError(Parser wr, Exception e) {
		ParseException err = wr.err(e.getClass().getName()+": "+e.getMessage());
		err.setStackTrace(e.getStackTrace());
		return err;
	}
}