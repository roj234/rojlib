package roj.config;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.config.data.*;
import roj.config.word.Word;
import roj.text.CharList;

import java.util.Map;

import static roj.config.JSONParser.*;
import static roj.config.word.Word.*;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class IniParser extends Parser<CMapping> {
	public static final int UNESCAPE = 4;

	private static final short eq = 14;
	private static final MyBitSet LB = MyBitSet.from(']');
	// readWord() checked WHITESPACE
	private static final MyBitSet iniSymbol_LN = MyBitSet.from("[]=\r\n");

	private static final TrieTree<Word> INI_TOKENS = new TrieTree<>();
	private static final MyBitSet INI_LENDS = new MyBitSet();
	static {
		addKeywords(INI_TOKENS, 10, "true", "false", "null");
		addSymbols(INI_TOKENS, INI_LENDS, 14, "=", "[", "]");
		addWhitespace(INI_LENDS);
	}
	{ tokens = INI_TOKENS; literalEnd = INI_LENDS; }

	public static CMapping parses(CharSequence string) throws ParseException {
		return new IniParser().parse(string, 0).asMap();
	}

	@Override
	public CMapping parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		try {
			CMapping map = new CMapping();

			String name = CMapping.CONFIG_TOPLEVEL;
			while (true) {
				try {
					map.put(name, iniValue(flags));
				} catch (ParseException e) {
					throw e.addPath('.' + name);
				}

				Word w = next();
				if (w.type() != left_m_bracket) break;
				name = readTill(LB); index++;
				if (name == null) break;

				if ((flags & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: " + name);
			}
			return map;
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}

	public int availableFlags() { return NO_DUPLICATE_KEY|UNESCAPE; }
	public String format() { return "INI"; }

	public CharList append(CEntry entry, int flag, CharList sb) { return entry.appendINI(sb); }

	private CEntry iniValue(int flag) throws ParseException {
		Word w = next();
		// EOF or not 'primitive'
		if (w.type() < 0 || w.type() > 12) {
			retractWord();
			return new CMapping();
		}

		String name = w.val();

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
			if (w.type() == left_m_bracket) break;
		}
		return list;
	}
	private CMapping iniMap(int flag) throws ParseException {
		Map<String, CEntry> map = new MyHashMap<>();

		while (true) {
			Word w = next();
			if (w.type() == EOF) break;
			if (w.type() == left_m_bracket) {
				retractWord();
				break;
			}

			String name = w.val();
			if ((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name)) throw err("重复的key: " + name);
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
		return new CMapping(map);
	}

	@SuppressWarnings("fallthrough")
	CEntry element(int flag) throws ParseException {
		literalEnd = iniSymbol_LN;
		Word w = next();

		String val = w.val();
		switch (w.type()) {
			case LITERAL:
				if ((flag & UNESCAPE) != 0 && val.startsWith("\"") && val.endsWith("\"")) {
					return CString.valueOf(val.substring(1, val.length()-1));
				}
			case STRING: return CString.valueOf(val);
			case DOUBLE:
			case FLOAT: return CDouble.valueOf(w.asDouble());
			case INTEGER: return CInteger.valueOf(w.asInt());
			case LONG: return CLong.valueOf(w.asLong());
			case TRUE:
			case FALSE: return CBoolean.valueOf(w.type() == TRUE);
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
					return NUMBER.contains(c) ? digitReader(false, 0) : readLiteral();
				} finally {
					literalEnd = INI_LENDS;
				}
			}
		}
		index = i;
		return eof();
	}

	public static boolean literalSafe(CharSequence key) {
		if (LITERAL_UNSAFE) return false;

		for (int i = 0; i < key.length(); i++) {
			if (INI_LENDS.contains(key.charAt(i))) return false;
		}
		return key.length() > 0;
	}

	@Override
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException {
		return readLiteral();
	}

	private String readTill(MyBitSet terminate) {
		CharSequence in = input;
		int i = index;

		if (i >= in.length()) return null;

		while (i < in.length()) {
			char c = in.charAt(i);
			if (terminate.contains(c)) {
				return in.subSequence(index, index = i).toString();
			}
			i++;
		}
		return null;
	}
}