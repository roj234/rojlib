package roj.config.data;

import org.jetbrains.annotations.Nullable;
import roj.config.serial.CConsumer;
import roj.config.serial.Structs;
import roj.config.serial.ToJson;
import roj.config.serial.ToYaml;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Map;

/**
 * Config Entry
 *
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public abstract class CEntry {
	protected CEntry() {}

	public abstract Type getType();

	public static final int Q_SET = 1, Q_SET_IF_ABSENT = 2, Q_SET_IF_NOT_SIMILAR = 4, Q_CREATE_MID = 8, Q_REPLACE_MID = 16, Q_RETURN_CONTAINER = 32;
	public final CEntry query(CharSequence sql, int flag, @Nullable CEntry def, CharList tmp) {
		CEntry node = this;

		tmp.clear();
		boolean quoted = false;

		int prevI = 0;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			switch (c) {
				case '"': quoted ^= true; continue;
				case '.': case '[': if (quoted) break;
					if (sql.charAt(prevI) != ']') {
						if (node.getType() != Type.MAP) throw new IllegalStateException("match failed: '" + sql.subSequence(0, prevI) + "' is not a MAP but " + node.getType());

						node = getInMap(node, tmp, flag, c=='[');
						tmp.clear();
						if (node == null) return def;
					}

					if (c == '[') {
						if (node.getType() != Type.LIST) throw new IllegalStateException("match failed: '" + sql.subSequence(0, i) + "' is not a LIST but " + node.getType());

						int j = ++i;
						foundEnd: {
							for (; j < sql.length(); j++) {
								if (sql.charAt(j) == ']') break foundEnd;
							}
							throw new IllegalArgumentException("invalid ListEq: not end: " + sql);
						}

						if (j==i) throw new IllegalArgumentException("invalid ListEq: not number: " + sql);
						if (tmp.length() > 0) throw new AssertionError();
						int pos = (int) ITokenizer.parseNumber(tmp.append(sql, i, j), TextUtil.INT_MAXS, 10, false);
						tmp.clear();

						if (j == sql.length()-1) {
							List<CEntry> list = node.asList().raw();
							if (list.size() <= pos) {
								if ((flag & Q_SET) == 0) return def;
								if (def != null) {
									while (list.size() <= pos) list.add(CNull.NULL);
									list.set(pos, def);
								}

								return null;
							} else {
								CEntry entry = node.asList().get(pos);

								set:
								if ((flag & Q_SET) != 0) {
									if (def == null) return list.remove(pos);

									if ((flag & Q_SET_IF_ABSENT) != 0 && entry != CNull.NULL) {
										break set;
									} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && entry.isSimilar(def)) {
										break set;
									}

									list.set(pos, def);
								}

								return entry;
							}
						} else if ((c=sql.charAt(j+1)) != '.' && c != '[') {
							throw new IllegalArgumentException("invalid ListEq: excepting '.' or '[' at "+(j+1)+": " + sql);
						} else if (c == '.') j++;

						node = node.asList().get(pos);
						i = j;
					}
					prevI = i;
					continue;
			}
			tmp.append(c);
		}

		if (node.getType() != Type.MAP) throw new IllegalStateException("match failed: '" + sql.subSequence(0, prevI) + "' is not a MAP but " + node.getType());

		if ((flag & Q_RETURN_CONTAINER) != 0) return node;

		Map<String, CEntry> map = node.asMap().raw();
		if ((flag & Q_SET) == 0) return map.getOrDefault(tmp, def);

		if (def == null) return map.remove(tmp);

		CEntry v = map.getOrDefault(tmp, CNull.NULL);
		if ((flag & Q_SET_IF_ABSENT) != 0 && v != CNull.NULL) {
			return v;
		} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && v.isSimilar(def)) {
			return v;
		}

		map.put(tmp.toString(), def);
		return def;
	}
	private static CEntry getInMap(CEntry node, CharList name, int opFlag, boolean list) {
		Map<String, CEntry> _map = node.asMap().raw();
		node = _map.getOrDefault(name, CNull.NULL);
		// 这里不对错误的type扔异常，sql和pos传进来浪费
		if (node.getType() == Type.NULL || (node.getType() != (list?Type.LIST:Type.MAP) && (opFlag & Q_REPLACE_MID) != 0)) {
			if ((opFlag & Q_CREATE_MID) != 0) {
				if (list) throw new IllegalStateException("Cannot create LIST");
				_map.put(name.toString(), node = new CMapping());
			} else {
				return null;
			}
		}
		return node;
	}

	////// easy caster

	public String asString() {
		throw new ClassCastException(getType() + " unable cast to 'string'");
	}
	public int asInteger() {
		throw new ClassCastException(getType() + " unable cast to 'int'");
	}
	public double asDouble() {
		throw new ClassCastException(getType() + " unable cast to 'double'");
	}
	public long asLong() {
		throw new ClassCastException(getType() + " unable cast to 'long'");
	}
	public CMapping asMap() {
		throw new ClassCastException(getType() + " unable cast to 'map'");
	}
	public CList asList() {
		throw new ClassCastException(getType() + " unable cast to 'list'");
	}
	public boolean asBool() {
		throw new ClassCastException(getType() + " unable cast to 'boolean'");
	}

	////// toString methods

	@Override
	public String toString() {
		return toShortJSON();
	}

	protected abstract StringBuilder toJSON(StringBuilder sb, int depth);

	public final String toJSON() {
		ToJson ser = new ToJson(4);
		forEachChild(ser);
		return ser.toString();
	}
	public final CharList toJSONb() {
		ToJson ser = new ToJson(4);
		forEachChild(ser);
		return ser.getValue();
	}
	public final String toShortJSON() {
		ToJson ser = new ToJson();
		forEachChild(ser);
		return ser.toString();
	}
	public final CharList toShortJSONb() {
		ToJson ser = new ToJson();
		forEachChild(ser);
		return ser.getValue();
	}

	public final String toYAML() {
		ToYaml ser = new ToYaml();
		forEachChild(ser);
		return ser.toString();
	}
	public final CharList toYAMLb() {
		ToYaml ser = new ToYaml();
		forEachChild(ser);
		return ser.getValue();
	}

	protected StringBuilder toINI(StringBuilder sb, int depth) {
		return toJSON(sb, 0);
	}
	public final String toINI() {
		return toINI(new StringBuilder(), 0).toString();
	}
	public final StringBuilder toINIb() {
		return toINI(new StringBuilder(), 0);
	}

	protected StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
		return toJSON(sb, 0);
	}
	public final String toTOML() {
		return toTOML(new StringBuilder(), 0, new CharList()).toString();
	}
	public final StringBuilder toTOMLb() {
		return toTOML(new StringBuilder(), 0, new CharList());
	}

	// Converting

	public abstract void forEachChild(CConsumer ser);

	public byte getNBTType() {
		throw new IllegalStateException("Didn't know how to serialize " + getType() + " to NBT");
	}
	public void toNBT(DynByteBuf w) {
		throw new IllegalStateException("Didn't know how to serialize " + getType() + " to NBT");
	}

	public abstract Object unwrap();

	public final void toBinary(DynByteBuf w) {
		toBinary(w, null);
	}
	public abstract void toBinary(DynByteBuf w, Structs struct);

	public void toB_encode(DynByteBuf w) {
		throw new IllegalStateException("Didn't know how to serialize " + getType() + " to B-encode");
	}

	public boolean isSimilar(CEntry o) {
		return getType().isSimilar(o.getType());
	}
}
