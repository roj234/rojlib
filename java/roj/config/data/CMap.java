package roj.config.data;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.config.TOMLParser;
import roj.config.Tokenizer;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CMap extends CEntry {
	public static final String CONFIG_TOPLEVEL = "<root>";

	final Map<String, CEntry> map;
	CharList dot;

	public CMap() { this.map = new MyHashMap<>(); }
	public CMap(Map<String, CEntry> map) { this.map = map; }
	public CMap(int size) { this.map = new MyHashMap<>(size); }

	public Type getType() { return Type.MAP; }

	public CMap asMap() { return this; }

	@Override
	public void accept(CVisitor ser) {
		ser.valueMap(map.size());
		if (!map.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				ser.key(entry.getKey());
				entry.getValue().accept(ser);
			}
		}
		ser.pop();
	}
	public void acceptOrdered(CVisitor ser, String... names) {
		ser.valueMap();
		for (String name : names) {
			CEntry value = map.get(name);
			if (value != null) {
				ser.key(name);
				value.accept(ser);
			}
		}
		ser.pop();
	}

	public final Map<String, CEntry> raw() { return map; }
	public final Map<String, Object> rawDeep() {
		MyHashMap<String, Object> caster = Helpers.cast(new MyHashMap<>(map));
		for (Map.Entry<String, Object> entry : caster.entrySet()) {
			entry.setValue(((CEntry) entry.getValue()).rawDeep());
		}
		return caster;
	}

	public final Set<Map.Entry<String, CEntry>> entrySet() { return map.entrySet(); }
	public final Set<String> keySet() { return map.keySet(); }
	public final Collection<CEntry> values() { return map.values(); }

	public final int size() { return map.size(); }
	public CharList dot(boolean dotMode) {
		if (dotMode == (dot == null)) this.dot = dotMode ? new CharList() : null;
		return dot;
	}

	// region PUT
	public final CEntry put(String key, CEntry entry) { return put1(key, entry == null ? CNull.NULL : entry, 0); }
	public final CEntry put(String key, @NotNull String entry) {
		Objects.requireNonNull(entry);

		CEntry prev = get(key);
		if (prev.getType() == Type.STRING) {
			((CString) prev).value = entry;
			return null;
		} else {
			return put1(key, CString.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, boolean entry) { return put1(key, CBoolean.valueOf(entry), 0); }
	public final CEntry put(String key, int entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.INTEGER) {
			((CInt) prev).value = entry;
			return null;
		} else {
			return put1(key, CInt.valueOf(entry), 0);
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

	public final CEntry putIfAbsent(String key, CEntry v) { return put1(key, v, Q_SET_IF_ABSENT);}
	public final String putIfAbsent(String key, String v) { return put1(key, CString.valueOf(v), Q_SET_IF_ABSENT).asString();}
	public final boolean putIfAbsent(String key, boolean v) { return put1(key, CBoolean.valueOf(v), Q_SET_IF_ABSENT).asBool();}
	public final int putIfAbsent(String key, int v) { return put1(key, CInt.valueOf(v), Q_SET_IF_ABSENT).asInt();}
	public final long putIfAbsent(String key, long v) { return put1(key, CLong.valueOf(v), Q_SET_IF_ABSENT).asLong();}
	public final double putIfAbsent(String key, double v) { return put1(key, CDouble.valueOf(v), Q_SET_IF_ABSENT).asDouble();}

	public final CMap getOrCreateMap(String key) { return put1(key, new CMap(), Q_SET_IF_ABSENT).asMap();}
	public final CList getOrCreateList(String key) { return put1(key, new CList(), Q_SET_IF_ABSENT).asList();}

	protected CEntry put1(String k, CEntry v, int f) {
		if (null == dot) {
			if ((f & Q_SET_IF_ABSENT) == 0 || !map.getOrDefault(k, CNull.NULL).mayCastTo(v.getType())) {
				map.put(k, v);
				return v;
			}
			return map.getOrDefault(k, v);
		}

		return query(k, Q_CREATE_MID|Q_SET|f, v, dot);
	}
	// endregion
	// region GET

	public final boolean containsKey(String key) {return getOr(key, null) != null;}
	public final boolean containsKey(String key, Type type) {return get(key).mayCastTo(type);}

	public final boolean getBool(String key) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.BOOL) && entry.asBool();
	}
	@NotNull
	public final String getString(String key) {return getString(key, "");}
	@Contract("_,!null -> !null")
	public final String getString(String key, String def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : def;
	}
	public final int getInteger(String key) {return getInteger(key, 0);}
	public final int getInteger(String key, int def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.INTEGER) ? entry.asInt() : def;
	}
	public final long getLong(String key) {return getLong(key, 0);}
	public final long getLong(String key, long def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.LONG) ? entry.asLong() : def;
	}
	public final float getFloat(String key) {return getFloat(key, 0);}
	public final float getFloat(String key, float def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.Float4) ? entry.asFloat() : def;
	}
	public final double getDouble(String key) {return getDouble(key, 0);}
	public final double getDouble(String key, double def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.DOUBLE) ? entry.asDouble() : def;
	}
	@NotNull
	public final CList getList(String key) {return get(key).asList();}
	@NotNull
	public final CMap getMap(String key) {return get(key).asMap();}
	@NotNull
	public final CEntry get(String key) {return getOr(key, CNull.NULL);}
	@NotNull
	public final CEntry getDot(String path) {return query(path, 0, CNull.NULL, dot == null ? new CharList() : dot);}
	@Nullable
	public final CEntry getOr(String k) {return getOr(k,null);}
	@Contract("_,!null -> !null")
	public CEntry getOr(String k, CEntry def) {
		if (k == null) return def;
		if (null == dot) return map.getOrDefault(k, def);
		return query(k, 0, CNull.NULL, dot);
	}
	// endregion

	/**
	 * 使自身符合def中定义的规则（字段名称，类型，数量）
	 * 多出的将被删除（可选）
	 */
	public final void format(CMap def, boolean deep, boolean remove) {
		Map<String, CEntry> map = this.map;
		for (Map.Entry<String, CEntry> entry : def.map.entrySet()) {
			String k = entry.getKey();

			CEntry myEnt = map.putIfAbsent(entry.getKey(), entry.getValue());
			if (myEnt == null) continue;
			CEntry oEnt = entry.getValue();

			if (!myEnt.mayCastTo(oEnt.getType())) {
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

	/**
	 * @param self 优先从自身合并
	 */
	public void merge(CMap o, boolean self, boolean deep) {
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
						if (myEnt.getType() == Type.MAP && oEnt.getType() == Type.MAP) {
							myEnt.asMap().merge(oEnt.asMap(), true, true);
						}
					}
				}
			} else {
				for (Map.Entry<String, CEntry> entry : o.map.entrySet()) {
					CEntry oEnt = entry.getValue();
					CEntry myEnt = map.put(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && oEnt.getType() == Type.MAP) {
							myEnt.asMap().merge(oEnt.asMap(), false, true);
							map.put(entry.getKey(), myEnt);
						}
					}
				}
			}
		}
	}

	public final void remove(String name) { map.remove(name); }

	public boolean isCommentSupported() {return false;}
	public String getComment(String key) {return null;}
	public void putComment(String key, String val) {throw new UnsupportedOperationException();}
	public CMap withComments() { return new CCommMap(map); }
	public void clearComments() {}

	protected final CharList toJSON(CharList sb, int depth) { throw new NoSuchMethodError(); }

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (!map.isEmpty()) {
			if (chain.length() > 0 && depth < 2) {
				if (TOMLParser.literalSafe(chain)) {
					sb.append('[').append(chain).append("]\n");
				} else {
					Tokenizer.addSlashes(sb.append('['), chain).append("]\n");
				}
			}
			if (depth == 0 && map.containsKey(CONFIG_TOPLEVEL))
				map.get(CONFIG_TOPLEVEL).toTOML(sb, 0, chain).append('\n');

			if (depth == 3) sb.append('{');
			for (Map.Entry<String, CEntry> entry : map.entrySet()) {
				String comment = getComment(entry.getKey());
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
							Tokenizer.addSlashes(sb.append('"'), chain).append('"');
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

	public int hashCode() { return map.hashCode(); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CMap mapping)) return false;
		return map.equals(mapping.map);
	}
}