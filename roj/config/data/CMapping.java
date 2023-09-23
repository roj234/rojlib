package roj.config.data;

import roj.collect.MyHashMap;
import roj.config.IniParser;
import roj.config.NBTParser;
import roj.config.TOMLParser;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CMapping extends CEntry {
	public static final String CONFIG_TOPLEVEL = "<root>";

	final Map<String, CEntry> map;
	CharList dot;

	public CMapping() { this.map = new MyHashMap<>(); }
	public CMapping(Map<String, CEntry> map) { this.map = map; }
	public CMapping(int size) { this.map = new MyHashMap<>(size); }

	public final int size() {
		return map.size();
	}

	public CharList dot(boolean dotMode) {
		if (dotMode == (dot == null)) this.dot = dotMode ? new CharList() : null;
		return dot;
	}

	@Override
	public Type getType() {
		return Type.MAP;
	}

	public final Map<String, CEntry> raw() {
		return map;
	}
	public final Set<String> keySet() {
		return map.keySet();
	}
	public final Set<Map.Entry<String, CEntry>> entrySet() {
		return map.entrySet();
	}
	public final Collection<CEntry> values() {
		return map.values();
	}

	// region PUT

	public final CEntry put(String key, CEntry entry) {
		return put1(key, entry == null ? CNull.NULL : entry, 0);
	}
	public final CEntry put(String key, String entry) {
		if (entry == null) return map.remove(key);

		CEntry prev = get(key);
		if (prev.getType() == Type.STRING) {
			((CString) prev).value = entry;
			return null;
		} else {
			return put1(key, CString.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, int entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.INTEGER) {
			((CInteger) prev).value = entry;
			return null;
		} else {
			return put1(key, CInteger.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, long entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.LONG) {
			((CLong) prev).value = entry;
			return null;
		} else {
			return put1(key, CLong.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, double entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.DOUBLE) {
			((CDouble) prev).value = entry;
			return null;
		} else {
			return put1(key, CDouble.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, boolean entry) {
		return put1(key, CBoolean.valueOf(entry), 0);
	}


	public final CEntry putIfAbsent(String key, CEntry v) {
		return put1(key, v, Q_SET_IF_ABSENT);
	}
	public final String putIfAbsent(String key, String v) {
		return put1(key, CString.valueOf(v), Q_SET_IF_ABSENT).asString();
	}
	public final boolean putIfAbsent(String key, boolean v) {
		return put1(key, CBoolean.valueOf(v), Q_SET_IF_ABSENT).asBool();
	}
	public final int putIfAbsent(String key, int v) {
		return put1(key, CInteger.valueOf(v), Q_SET_IF_ABSENT).asInteger();
	}
	public final long putIfAbsent(String key, long v) {
		return put1(key, CLong.valueOf(v), Q_SET_IF_ABSENT).asLong();
	}
	public final double putIfAbsent(String key, double v) {
		return put1(key, CDouble.valueOf(v), Q_SET_IF_ABSENT).asDouble();
	}
	public final CMapping getOrCreateMap(String key) {
		return put1(key, new CMapping(), Q_SET_IF_ABSENT).asMap();
	}
	public final CList getOrCreateList(String key) {
		return put1(key, new CList(), Q_SET_IF_ABSENT).asList();
	}

	private CEntry put1(String k, CEntry v, int f) {
		if (null == dot) {
			if ((f & Q_SET_IF_ABSENT) == 0 || !map.getOrDefault(k, CNull.NULL).isSimilar(v)) {
				map.put(k, v);
				return v;
			}
			return map.getOrDefault(k, v);
		}

		return query(k, Q_CREATE_MID|Q_SET|f, v, dot);
	}

	/**
	 * 使自身符合def中定义的规则（字段名称，类型，数量）
	 * 多出的将被删除（可选）
	 */
	public final void format(CMapping def, boolean deep, boolean remove) {
		Map<String, CEntry> map = this.map;
		for (Map.Entry<String, CEntry> entry : def.map.entrySet()) {
			String k = entry.getKey();

			CEntry myEnt = map.putIfAbsent(entry.getKey(), entry.getValue());
			if (myEnt == null) continue;
			CEntry oEnt = entry.getValue();

			if (!myEnt.getType().isSimilar(oEnt.getType())) {
				map.put(k, oEnt);
			} else if (myEnt.getType() == Type.MAP && deep) {
				myEnt.asMap().format(oEnt.asMap(), true, remove);
			}
		}
		if (remove) {
			for (Iterator<Map.Entry<String, CEntry>> itr = map.entrySet().iterator(); itr.hasNext(); ) {
				if (!def.map.containsKey(itr.next().getKey())) {
					itr.remove();
				}
			}
		}
	}

	// endregion
	// region GET

	public final boolean containsKey(String key) {
		return get1(key, null) != null;
	}
	public final boolean containsKey(String key, Type type) {
		return type.isSimilar(get(key).getType());
	}

	public final boolean getBool(String key) {
		CEntry entry = get(key);
		return Type.BOOL.isSimilar(entry.getType()) && entry.asBool();
	}
	public final String getString(String key) {
		CEntry entry = get(key);
		return Type.STRING.isSimilar(entry.getType()) ? entry.asString() : "";
	}
	public final String getString(String key, String def) {
		CEntry entry = get(key);
		return Type.STRING.isSimilar(entry.getType()) ? entry.asString() : def;
	}
	public final int getInteger(String key) {
		CEntry entry = get(key);
		return entry.isNumber() ? entry.asInteger() : 0;
	}
	public final long getLong(String key) {
		CEntry entry = get(key);
		return entry.isNumber() ? entry.asLong() : 0;
	}
	public final double getDouble(String key) {
		CEntry entry = get(key);
		return entry.isNumber() ? entry.asDouble() : 0;
	}
	public final CList getList(String key) { return get(key).asList(); }
	public final CMapping getMap(String key) { return get(key).asMap(); }

	public final CEntry get(String key) {
		return get1(key, CNull.NULL);
	}
	public final CEntry getOrNull(String key) {
		return get1(key, null);
	}
	public final CEntry getDot(String path) {
		return query(path, 0, CNull.NULL, dot == null ? new CharList() : dot);
	}

	private CEntry get1(String k, CEntry def) {
		if (k == null) return def;
		if (null == dot) return map.getOrDefault(k, def);
		return query(k, 0, CNull.NULL, dot);
	}

	// endregion

	/**
	 * @param self 优先从自身合并
	 */
	public void merge(CMapping o, boolean self, boolean deep) {
		if (!deep) {
			if (!self) {
				map.putAll(o.map);
			} else {
				for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
					map.putIfAbsent(entry.getKey(), entry.getValue());
				}
			}
		} else {
			if (self) {
				for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
					CEntry oEnt = entry.getValue();
					CEntry myEnt = map.putIfAbsent(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && Type.MAP.isSimilar(oEnt.getType())) {
							myEnt.asMap().merge(oEnt.asMap(), true, true);
						}
					}
				}
			} else {
				for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
					CEntry oEnt = entry.getValue();
					CEntry myEnt = map.put(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && Type.MAP.isSimilar(oEnt.getType())) {
							myEnt.asMap().merge(oEnt.asMap(), false, true);
							map.put(entry.getKey(), myEnt);
						}
					}
				}
			}
		}
	}

	public final void remove(String name) {
		map.remove(name);
	}

	public final void clear() {
		map.clear();
	}

	@Override
	public CMapping asMap() {
		return this;
	}

	protected String getCommentInternal(String key) {return null;}
	public boolean isCommentSupported() {return false;}
	public Map<String, String> getComments() {return Collections.emptyMap();}
	public void putCommentDotted(String key, String val) {throw new UnsupportedOperationException();}
	public CMapping withComments() { return new CCommMap(map); }
	public void clearComments(boolean withSub) {
		if (withSub) {
			for (CEntry entry : map.values()) {
				if (entry.getType() == Type.MAP) {
					entry.asMap().clearComments(true);
				}
			}
		}
	}

	protected final CharList toJSON(CharList sb, int depth) { throw new NoSuchMethodError(); }

	@Override
	public byte getNBTType() { return NBTParser.COMPOUND; }

	@Override
	public CharList toINI(CharList sb, int depth) {
		if (!map.isEmpty()) {
			Iterator<Map.Entry<String, CEntry>> itr = map.entrySet().iterator();
			if (depth == 0) {
				CEntry root = map.get(CONFIG_TOPLEVEL);
				if (root != null) root.toINI(sb, 1);

				while (itr.hasNext()) {
					Map.Entry<String, CEntry> entry = itr.next();

					String key = entry.getKey();
					if (key.equals(CONFIG_TOPLEVEL)) continue;

					String comment = getCommentInternal(entry.getKey());
					if (comment != null && comment.length() > 0) {
						addComments(sb, 0, comment, ";", "\n");
					}

					sb.append('[');
					if (key.indexOf(']') >= 0) {
						ITokenizer.addSlashes(sb.append('"'), entry.getKey()).append('"');
					} else {
						sb.append(key);
					}
					sb.append(']').append('\n');

					entry.getValue().toINI(sb, 1);
					if (!itr.hasNext()) break;
					sb.append('\n');
				}
			} else if (depth == 1) {
				while (itr.hasNext()) {
					Map.Entry<String, CEntry> entry = itr.next();

					String key = entry.getKey();
					boolean safe = IniParser.literalSafe(key);

					CEntry v = entry.getValue();
					if (v.getType() == Type.LIST) {
						List<CEntry> list = v.asList().raw();
						for (int i = 0; i < list.size(); i++) {
							if (safe) {
								sb.append(key);
							} else {
								ITokenizer.addSlashes(sb.append('"'), entry.getKey()).append('"');
							}
							list.get(i).toINI(sb.append('='), 2);
							sb.append('\n');
						}
					} else {
						if (safe) {
							sb.append(key);
						} else {
							ITokenizer.addSlashes(sb.append('"'), entry.getKey()).append('"');
						}

						v.toINI(sb.append('='), 2).append('\n');
					}
				}
			} else {
				throw new IllegalArgumentException("INI不支持两级以上的映射");
			}
		}
		return sb;
	}

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (!map.isEmpty()) {
			if (chain.length() > 0 && depth < 2) {
				if (TOMLParser.literalSafe(chain)) {
					sb.append('[').append(chain).append("]\n");
				} else {
					ITokenizer.addSlashes(sb.append('['), chain).append("]\n");
				}
			}
			if (depth == 0 && map.containsKey(CONFIG_TOPLEVEL))
				map.get(CONFIG_TOPLEVEL).toTOML(sb, 0, chain).append('\n');

			if (depth == 3) sb.append('{');
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				String comment = getCommentInternal(entry.getKey());
				if (comment != null && comment.length() > 0) {
					addComments(sb, 0, comment, "#", "\n");
				}

				CEntry v = entry.getValue();
				switch (v.getType()) {
					case MAP:
						if (entry.getKey().equals(CONFIG_TOPLEVEL) && depth == 0) continue;
						v.toTOML(sb, depth == 3 ? 3 : 1, entry.getKey()).append('\n');
						break;
					case LIST:
						v.toTOML(sb, depth == 3 ? 3 : 0, entry.getKey()).append('\n');
						break;
					default:
						if (TOMLParser.literalSafe(entry.getKey())) {
							sb.append(entry.getKey());
						} else {
							ITokenizer.addSlashes(sb.append('"'), chain).append('"');
						}
						sb.append(" = ");
						if (depth == 3) {
							v.toTOML(sb, 0, "").append(", ");
						} else {
							v.toTOML(sb, 0, "").append('\n');
						}
				}
			}
			sb.delete(sb.length() - (depth == 3 ? 2 : 0), sb.length());
			if (depth == 3) sb.append('}');
		}
		return sb;
	}

	@SuppressWarnings("fallthrough")
	public static void addComments(CharList sb, int depth, CharSequence com, CharSequence prefix, CharSequence postfix) {
		int r = 0, i = 0, prev = 0;
		while (i < com.length()) {
			switch (com.charAt(i)) {
				case '\r':
					if (i + 1 >= com.length() || com.charAt(i + 1) != '\n') {
						break;
					} else {
						r = 1;
						i++;
					}
				case '\n':
					if (prev != i) {
						for (int j = 0; j < depth; j++) sb.append(' ');
						sb.append(prefix).append(com, prev, i - r).append(postfix);
					}
					prev = i + 1;
					r = 0;
					break;
			}
			i++;
		}

		if (prev != i) {
			for (int j = 0; j < depth; j++) sb.append(' ');
			sb.append(prefix).append(com, prev, i).append(postfix);
		}
	}

	@Override
	public Object unwrap() {
		MyHashMap<String, Object> caster = Helpers.cast(new MyHashMap<>(map));
		for (Map.Entry<String, Object> entry : caster.entrySet()) {
			entry.setValue(((CEntry) entry.getValue()).unwrap());
		}
		return caster;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		if (struct != null) {
			String[] sortedId = struct.tryCompress(map, w);
			if (sortedId != null) {
				for (String id : sortedId) map.get(id).toBinary(w,struct);
				return;
			}
		}

		w.put((byte) Type.MAP.ordinal()).putVUInt(map.size());
		if (map.isEmpty()) return;
		for (Map.Entry<String, CEntry> entry : map.entrySet()) {
			entry.getValue().toBinary(w.putZhCn(entry.getKey()), struct);
		}
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.put((byte) 'd');
		if (!map.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				String k = entry.getKey();
				w.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(k))).put((byte)':').putUTFData(k);

				entry.getValue().toB_encode(w);
			}
		}
		w.put((byte) 'e');
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CMapping mapping = (CMapping) o;
		return map.equals(mapping.map);
	}
	public int hashCode() { return map.hashCode(); }

	@Override
	public void forEachChild(CVisitor ser) {
		ser.valueMap(map.size());
		if (!map.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				ser.key(entry.getKey());
				entry.getValue().forEachChild(ser);
			}
		}
		ser.pop();
	}

	public void forEachChildSorted(CVisitor ser, String... names) {
		ser.valueMap();
		for (String name : names) {
			CEntry value = map.get(name);
			if (value != null) {
				ser.key(name);
				value.forEachChild(ser);
			}
		}
		ser.pop();
	}
}
