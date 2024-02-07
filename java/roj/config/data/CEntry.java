package roj.config.data;

import org.jetbrains.annotations.Nullable;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.config.serial.ToJson;
import roj.config.serial.ToYaml;
import roj.io.IOUtil;
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
	protected boolean isNumber() { return getType().isNumber(); }

	public static final int Q_SET = 1, Q_SET_IF_ABSENT = 2, Q_SET_IF_NOT_SIMILAR = 4, Q_CREATE_MID = 8, Q_REPLACE_MID = 16, Q_RETURN_CONTAINER = 32;
	public final CEntry query(CharSequence sql) {
		return query(sql, 0, null, IOUtil.getSharedCharBuf());
	}
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
						if (node.getType() != Type.MAP) throw new IllegalStateException("期待映射 '" + sql.subSequence(0, prevI) + "' 实际为 " + node.getType());

						node = getInMap(node, tmp, flag, c=='[');
						tmp.clear();
						if (node == null) return def;
					}

					if (c == '[') {
						if (node.getType() != Type.LIST) throw new IllegalStateException("期待列表 '" + sql.subSequence(0, i) + "' 实际为 " + node.getType());

						int j = ++i;
						foundEnd: {
							for (; j < sql.length(); j++) {
								if (sql.charAt(j) == ']') break foundEnd;
							}
							throw new IllegalArgumentException("invalid ListEq: not end: " + sql);
						}

						if (j==i) throw new IllegalArgumentException("invalid ListEq: not number: " + sql);
						if (tmp.length() > 0) throw new AssertionError();
						int pos = TextUtil.parseInt(tmp.append(sql, i, j));
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

	public String asString() { throw new ClassCastException(getType() + "不是字符串"); }
	public int asInteger() { throw new ClassCastException(getType() + "不是整数"); }
	public double asDouble() { throw new ClassCastException(getType() + "不是浮点数"); }
	public long asLong() { throw new ClassCastException(getType() + "不是长整数"); }
	public boolean asBool() { throw new ClassCastException(getType() + "不是布尔值"); }
	public CMapping asMap() { throw new ClassCastException(getType() + "不是地图(迫真)"); }
	public CList asList() { throw new ClassCastException(getType() + "不是列表"); }

	////// toString methods

	@Override
	public String toString() { return toShortJSON(); }

	protected abstract CharList toJSON(CharList sb, int depth);

	public final String toJSON() {
		ToJson ser = new ToJson(4);
		forEachChild(ser.sb(IOUtil.getSharedCharBuf()));
		return ser.toString();
	}
	public final CharList toJSONb() {
		ToJson ser = new ToJson(4);
		forEachChild(ser);
		return ser.getValue();
	}
	public final String toShortJSON() {
		ToJson ser = new ToJson();
		forEachChild(ser.sb(IOUtil.getSharedCharBuf()));
		return ser.toString();
	}
	public final CharList toShortJSONb() {
		ToJson ser = new ToJson();
		forEachChild(ser);
		return ser.getValue();
	}

	public final String toYAML() {
		ToYaml ser = new ToYaml();
		forEachChild(ser.sb(IOUtil.getSharedCharBuf()));
		return ser.toString();
	}
	public final CharList toYAMLb() {
		ToYaml ser = new ToYaml();
		forEachChild(ser);
		return ser.getValue();
	}

	protected CharList toINI(CharList sb, int depth) { return toJSON(sb, 0); }
	public final String toINI() { return toINI(IOUtil.getSharedCharBuf(), 0).toString(); }
	public final CharList toINIb() { return toINI(new CharList(), 0); }
	public final CharList appendINI(CharList sb) { return toINI(sb, 0); }

	protected CharList toTOML(CharList sb, int depth, CharSequence chain) { return toJSON(sb, 0); }
	public final String toTOML() { return toTOML(IOUtil.getSharedCharBuf(), 0, new CharList()).toString(); }
	public final CharList toTOMLb() { return toTOML(new CharList(), 0, new CharList()); }
	public final CharList appendTOML(CharList sb, CharList tmp) { return toTOML(sb, 0, tmp); }

	// Converting

	public abstract void forEachChild(CVisitor ser);

	//@MagicConstant(valuesFromClass = NBTParser.class)
	public byte getNBTType() { throw new ClassCastException(getType()+"无法序列化为NBT"); }

	public abstract Object unwrap();

	public final DynByteBuf toBinary(DynByteBuf w) {
		toBinary(w, null);
		return w;
	}
	public final void _toBinary(DynByteBuf w, VinaryParser struct) {
		struct.reset();
		toBinary(w, struct);
	}
	protected abstract void toBinary(DynByteBuf w, @Nullable VinaryParser struct);

	public void toB_encode(DynByteBuf w) { throw new ClassCastException(getType() + "无法序列化为B-encode"); }

	public boolean isSimilar(CEntry o) { return getType().isSimilar(o.getType()); }
}