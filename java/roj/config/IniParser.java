package roj.config;

import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.text.Interner;
import roj.text.TextUtil;

import java.util.Map;

import static roj.config.Flags.NO_DUPLICATE_KEY;
import static roj.config.Flags.ORDERED_MAP;
import static roj.config.JSONParser.*;
import static roj.config.Word.*;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class IniParser extends Parser {
	public static final int UNESCAPE = 8;

	private static final short eq = rBrace;
	// readWord() checked WHITESPACE
	private static final BitSet iniSymbol_LN = BitSet.from("\r\n");

	private static final TrieTree<Word> INI_TOKENS = new TrieTree<>();
	private static final BitSet INI_LENDS = new BitSet();
	static {
		addKeywords(INI_TOKENS, TRUE, "true", "false", "null");
		addSymbols(INI_TOKENS, INI_LENDS, rBrace, "=", "[", "]");
		addWhitespace(INI_LENDS);
	}
	{ tokens = INI_TOKENS; literalEnd = INI_LENDS; }

	@Override
	public CMap parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		try {
			HashMap<String, CEntry> map = new LinkedHashMap<>();

			String name = CMap.CONFIG_TOPLEVEL;
			while (true) {
				try {
					map.put(name, iniValue(flags));
				} catch (ParseException e) {
					throw e.addPath('.'+name);
				}

				Word w = next();
				if (w.type() != lBracket) break;

				var in = input;
				int j = TextUtil.gIndexOf(in, ']', index);
				if (j < 0) throw err("no more");
				name = Interner.intern(in, index, j);
				index = j+1;

				if ((flags & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: "+name);
			}
			return new CMap(map);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}

	public Map<String, Integer> dynamicFlags() { return Map.of("NoDuplicateKey", NO_DUPLICATE_KEY, "Unescape", UNESCAPE, "OrderedMap", ORDERED_MAP); }
	public ConfigMaster format() { return ConfigMaster.INI; }

	private CEntry iniValue(int flag) throws ParseException {
		Word w = next();
		// EOF or not 'primitive'
		if (w.type() < 0 || w.type() > 12) {
			retractWord();
			return new CMap();
		}

		boolean eq1 = readWord().type() == eq;
		retractWord();
		return eq1 ? iniMap(flag) : iniList(flag);
	}
	private CList iniList(int flag) throws ParseException {
		CList list = new CList();
		while (true) {
			list.add(element(flag));

			Word w = next();
			retractWord();

			if (w.type() == EOF) break;
			if (w.type() == lBracket) break;
		}
		return list;
	}
	private CMap iniMap(int flag) throws ParseException {
		HashMap<String, CEntry> map = (flag&ORDERED_MAP) != 0 ? new LinkedHashMap<>() : new HashMap<>();

		while (true) {
			Word w = next();
			if (w.type() == EOF) break;
			if (w.type() == lBracket) {
				retractWord();
				break;
			}

			String name = Interner.intern(w.val());
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: "+name);
			except(eq, "=");
			try {
				CEntry prev = map.get(name);
				CEntry val = element(flag);
				if (prev == null) {
					map.put(name, val);
				} else {
					if (prev.getType() != Type.LIST) {
						map.put(name, prev = new CList().add(map.get(name)));
					}
					prev.asList().add(val);
				}
			} catch (ParseException e) {
				throw e.addPath('.' + name);
			}
		}
		return new CMap(map);
	}

	@SuppressWarnings("fallthrough")
	CEntry element(int flag) throws ParseException {
		literalEnd = iniSymbol_LN;
		Word w = next();

		String val = w.val();
		switch (w.type()) {
			case LITERAL:
				if ((flag & UNESCAPE) != 0 && val.startsWith("\"") && val.endsWith("\"")) {
					return CEntry.valueOf(val.substring(1, val.length()-1));
				}
			case STRING: return CEntry.valueOf(val);
			case DOUBLE:
			case FLOAT: return CEntry.valueOf(w.asDouble());
			case INTEGER: return CEntry.valueOf(w.asInt());
			case LONG: return CEntry.valueOf(w.asLong());
			case TRUE:
			case FALSE: return CEntry.valueOf(w.type() == TRUE);
			case NULL: return CNull.NULL;
			default: unexpected(val); return null;
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
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

	@Override
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
}