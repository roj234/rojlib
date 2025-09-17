package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.node.*;
import roj.text.ParseException;
import roj.text.TextUtil;
import roj.text.Token;

import static roj.config.JsonParser.*;
import static roj.config.node.ConfigValue.valueOf;
import static roj.text.Token.*;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class IniParser extends Parser {
	public static final int UNESCAPE = 8;

	private static final short eq = rBrace;
	// readWord() checked WHITESPACE
	private static final BitSet iniSymbol_LN = BitSet.from("\r\n");

	private static final TrieTree<Token> INI_TOKENS = new TrieTree<>();
	private static final BitSet INI_LENDS = new BitSet();
	static {
		addKeywords(INI_TOKENS, TRUE, "true", "false", "null");
		addSymbols(INI_TOKENS, INI_LENDS, rBrace, "=", "[", "]");
		addWhitespace(INI_LENDS);
	}
	{ tokens = INI_TOKENS; literalEnd = INI_LENDS; }

	@Override
	public MapValue parse(CharSequence text, @MagicConstant(flags = {NO_DUPLICATE_KEY, UNESCAPE, ORDERED_MAP}) int flags) throws ParseException {
		this.flag = flags;
		init(text);
		try {
			HashMap<String, ConfigValue> map = new LinkedHashMap<>();

			String name = MapValue.CONFIG_TOPLEVEL;
			while (true) {
				try {
					map.put(name, iniValue(flags));
				} catch (ParseException e) {
					throw e.addPath('.'+name);
				}

				Token w = next();
				if (w.type() != lBracket) break;

				var in = input;
				int j = TextUtil.indexOf(in, ']', index);
				if (j < 0) throw err("no more");
				name = in.subSequence(index, j).toString().intern();
				index = j+1;

				if ((flags & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: "+name);
			}
			return new MapValue(map);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}

	@SuppressWarnings("fallthrough")
	protected ConfigValue element(@MagicConstant(flags = {NO_DUPLICATE_KEY, UNESCAPE, ORDERED_MAP}) int flags) throws ParseException {
		literalEnd = iniSymbol_LN;
		Token w = next();

		String val = w.text();
		switch (w.type()) {
			case LITERAL:
				if ((flags & UNESCAPE) != 0 && val.startsWith("\"") && val.endsWith("\"")) {
					return valueOf(val.substring(1, val.length()-1));
				}
			case STRING: return valueOf(val);
			case DOUBLE, FLOAT: return valueOf(w.asDouble());
			case INTEGER: return valueOf(w.asInt());
			case LONG: return valueOf(w.asLong());
			case TRUE, FALSE: return valueOf(w.type() == TRUE);
			case NULL: return NullValue.NULL;
			default: unexpected(val); return null;
		}
	}

	private ConfigValue iniValue(int flag) throws ParseException {
		Token w = next();
		// EOF or not 'primitive'
		if (w.type() < 0 || w.type() > 12) {
			retractWord();
			return new MapValue();
		}

		boolean eq1 = readWord().type() == eq;
		retractWord();
		return eq1 ? iniMap(flag) : iniList(flag);
	}
	private ListValue iniList(int flag) throws ParseException {
		ListValue list = new ListValue();
		while (true) {
			list.add(element(flag));

			Token w = next();
			retractWord();

			if (w.type() == EOF) break;
			if (w.type() == lBracket) break;
		}
		return list;
	}
	private MapValue iniMap(int flag) throws ParseException {
		HashMap<String, ConfigValue> map = (flag&ORDERED_MAP) != 0 ? new LinkedHashMap<>() : new HashMap<>();

		while (true) {
			Token w = next();
			if (w.type() == EOF) break;
			if (w.type() == lBracket) {
				retractWord();
				break;
			}

			String name = w.text().intern();
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: "+name);
			except(eq, "=");
			try {
				ConfigValue prev = map.get(name);
				ConfigValue val = element(flag);
				if (prev == null) {
					map.put(name, val);
				} else {
					if (prev.getType() != Type.LIST) {
						map.put(name, prev = new ListValue().add(map.get(name)));
					}
					prev.asList().add(val);
				}
			} catch (ParseException e) {
				throw e.addPath('.' + name);
			}
		}
		return new MapValue(map);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Token readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i++);
			if (!WHITESPACE.contains(c)) {
				if (c == ';') {
					int s = i;
					while (i < in.length()) { // 单行注释
						c = in.charAt(i++);
						if (c == '\r' || c == '\n') {
							if (c == '\r' && i < in.length() && in.charAt(i) == '\n') i++;
							break;
						}
					}
					continue;
				}

				index = i-1;
				try {
					return NUMBER.contains(c) ? digitReader(false, 0) : readSymbol();
				} finally {
					literalEnd = INI_LENDS;
				}
			}
		}
		index = i;
		return eof();
	}

	public static boolean literalSafe(CharSequence key) {
		if (ALWAYS_ESCAPE) return false;

		for (int i = 0; i < key.length(); i++) {
			if (iniSymbol_LN.contains(key.charAt(i))) return false;
		}
		return key.length() > 0;
	}
}