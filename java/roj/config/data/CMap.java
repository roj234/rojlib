package roj.config.data;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.config.TOMLParser;
import roj.config.Tokenizer;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CMap extends CEntry {
	/**
	 * 部分配置格式，例如TOML的顶层元素的名称
	 */
	public static final String CONFIG_TOPLEVEL = "<root>";

	protected Map<String, CEntry> properties;
	boolean dot;

	public CMap() { properties = new HashMap<>(); }
	public CMap(int initialCapacity) { properties = new HashMap<>(initialCapacity); }
	public CMap(Map<String, CEntry> properties) { this.properties = properties; }

	public Type getType() { return Type.MAP; }

	public CMap asMap() { return this; }

	@Override
	public void accept(CVisitor visitor) {
		visitor.valueMap(properties.size());
		if (!properties.isEmpty()) {
			for (var entry : properties.entrySet()) {
				visitor.key(entry.getKey());
				entry.getValue().accept(visitor);
			}
		}
		visitor.pop();
	}
	public void accept(CVisitor visitor, String... elementOrder) {
		visitor.valueMap();
		for (String name : elementOrder) {
			CEntry value = properties.get(name);
			if (value != null) {
				visitor.key(name);
				value.accept(visitor);
			}
		}
		visitor.pop();
	}

	public final Map<String, CEntry> raw() { return properties; }
	public final Map<String, Object> unwrap() {
		HashMap<String, Object> rawdata = Helpers.cast(properties instanceof LinkedHashMap ? new LinkedHashMap<>(properties) : new HashMap<>(properties));
		for (var entry : rawdata.entrySet()) {
			entry.setValue(((CEntry) entry.getValue()).unwrap());
		}
		return rawdata;
	}

	public final Set<Map.Entry<String, CEntry>> entrySet() { return properties.entrySet(); }
	public final Set<String> keySet() { return properties.keySet(); }
	public final Collection<CEntry> values() { return properties.values(); }

	public boolean isEmpty() {return properties.isEmpty();}
	public final int size() {return properties.size();}
	public void dot(boolean dotMode) {this.dot = dotMode;}

	// region PUT
	public final CEntry put(String key, CEntry entry) { return put(key, entry == null ? CNull.NULL : entry, 0); }
	public final CEntry put(String key, @NotNull String entry) {
		Objects.requireNonNull(entry);

		CEntry prev = get(key);
		if (prev.getType() == Type.STRING) {
			((CString) prev).value = entry;
			return null;
		} else {
			return put(key, CEntry.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, boolean entry) { return put(key, CEntry.valueOf(entry), 0); }
	public final CEntry put(String key, int entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.INTEGER) {
			((CInt) prev).value = entry;
			return null;
		} else {
			return put(key, CEntry.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, long entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.LONG) {
			((CLong) prev).value = entry;
			return null;
		} else {
			return put(key, CEntry.valueOf(entry), 0);
		}
	}
	public final CEntry put(String key, double entry) {
		CEntry prev = get(key);
		if (prev.getType() == Type.DOUBLE) {
			((CDouble) prev).value = entry;
			return null;
		} else {
			return put(key, CEntry.valueOf(entry), 0);
		}
	}

	public final CEntry putIfAbsent(String key, CEntry v) { return put(key, v, Q_SET_IF_ABSENT);}
	public final String putIfAbsent(String key, String v) { return put(key, CEntry.valueOf(v), Q_SET_IF_ABSENT).asString();}
	public final boolean putIfAbsent(String key, boolean v) { return put(key, CEntry.valueOf(v), Q_SET_IF_ABSENT).asBool();}
	public final int putIfAbsent(String key, int v) { return put(key, CEntry.valueOf(v), Q_SET_IF_ABSENT).asInt();}
	public final long putIfAbsent(String key, long v) { return put(key, CEntry.valueOf(v), Q_SET_IF_ABSENT).asLong();}
	public final double putIfAbsent(String key, double v) { return put(key, CEntry.valueOf(v), Q_SET_IF_ABSENT).asDouble();}

	public final CMap getOrCreateMap(String key) { return put(key, new CMap(), Q_SET_IF_ABSENT).asMap();}
	public final CList getOrCreateList(String key) { return put(key, new CList(), Q_SET_IF_ABSENT).asList();}

	protected CEntry put(String k, CEntry v, int flag) {
		if (!dot) {
			if ((flag & Q_SET_IF_ABSENT) == 0 || !properties.getOrDefault(k, CNull.NULL).mayCastTo(v.getType())) {
				properties.put(k, v);
				return v;
			}
			return properties.getOrDefault(k, v);
		}

		return query(k, Q_CREATE_MID|Q_SET|flag, v);
	}
	// endregion
	// region GET

	public final boolean containsKey(String key) {return getOr(key, null) != null;}
	public final boolean containsKey(String key, Type type) {return get(key).mayCastTo(type);}

	public final boolean getBool(String key) {return getBool(key, false);}
	public final boolean getBool(String key, boolean def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.BOOL) ? entry.asBool() : def;
	}
	@NotNull
	public final String getString(String key) {return getString(key, "");}
	@Contract("_,!null -> !null")
	public final String getString(String key, String def) {
		CEntry entry = get(key);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : def;
	}
	public final int getInteger(String key) {return getInt(key, 0);}
	public final int getInteger(String key, int def) {return getInt(key, def);}
	public final int getInt(String key) {return getInt(key, 0);}
	public final int getInt(String key, int def) {
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
	@NotNull public CList getList(String key) {return get(key).asList();}
	@NotNull public CMap getMap(String key) {return get(key).asMap();}

	@NotNull public CEntry get(String key) {return getOr(key, CNull.NULL);}
	@NotNull public final CEntry getDot(String path) {return query(path, 0, CNull.NULL);}
	@Nullable public final CEntry getOr(String k) {return getOr(k,null);}
	@Contract("_,!null -> !null") public CEntry getOr(String k, CEntry def) {
		if (k == null) return def;
		return !dot ? properties.getOrDefault(k, def) : query(k, 0, CNull.NULL);
	}
	// endregion
	public final void remove(String name) { properties.remove(name); }

	////// comments

	public boolean isCommentable() {return false;}
	public CMap toCommentable() {return new Commentable();}
	public String getComment(String key) {return null;}
	public void setComment(String key, String val) {throw new UnsupportedOperationException();}
	public void clearComments() {}

	/**
	 * 使自身符合rule中定义的规则（字段名称，类型，数量）
	 * 多出的将被删除（可选）
	 */
	public final void format(CMap rule, boolean deep, boolean remove) {
		Map<String, CEntry> map = this.properties;
		for (var entry : rule.properties.entrySet()) {
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
			for (var itr = map.entrySet().iterator(); itr.hasNext(); ) {
				if (!rule.properties.containsKey(itr.next().getKey())) {
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
				properties.putAll(o.properties);
			} else {
				for (var entry : o.properties.entrySet()) {
					properties.putIfAbsent(entry.getKey(), entry.getValue());
				}
			}
		} else {
			if (self) {
				for (var entry : o.properties.entrySet()) {
					CEntry oEnt = entry.getValue();
					CEntry myEnt = properties.putIfAbsent(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && oEnt.getType() == Type.MAP) {
							myEnt.asMap().merge(oEnt.asMap(), true, true);
						}
					}
				}
			} else {
				for (var entry : o.properties.entrySet()) {
					CEntry oEnt = entry.getValue();
					CEntry myEnt = properties.put(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && oEnt.getType() == Type.MAP) {
							myEnt.asMap().merge(oEnt.asMap(), false, true);
							properties.put(entry.getKey(), myEnt);
						}
					}
				}
			}
		}
	}

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (!properties.isEmpty()) {
			if (chain.length() > 0 && depth < 2) {
				if (TOMLParser.literalSafe(chain)) {
					sb.append('[').append(chain).append("]\n");
				} else {
					Tokenizer.escape(sb.append('['), chain).append("]\n");
				}
			}
			if (depth == 0 && properties.containsKey(CONFIG_TOPLEVEL))
				properties.get(CONFIG_TOPLEVEL).toTOML(sb, 0, chain).append('\n');

			if (depth == 3) sb.append('{');
			for (var entry : properties.entrySet()) {
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
							Tokenizer.escape(sb.append('"'), chain).append('"');
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

	public int hashCode() { return properties.hashCode(); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CMap mapping)) return false;
		return properties.equals(mapping.properties);
	}

	public static class Commentable extends CMap {
		public Map<String, String> comments = new HashMap<>();

		public Commentable() {}
		public Commentable(Map<String, CEntry> properties) { super(properties); }
		public Commentable(Map<String, CEntry> properties, Map<String, String> comments) {
			super(properties);
			this.comments = comments;
		}

		@Override
		public void accept(CVisitor visitor) {
			visitor.valueMap();
			if (!properties.isEmpty()) {
				for (var entry : properties.entrySet()) {
					String comment = comments.get(entry.getKey());
					if (comment != null) visitor.comment(comment);

					visitor.key(entry.getKey());
					entry.getValue().accept(visitor);
				}
			}
			visitor.pop();
		}

		public void accept(CVisitor visitor, String... elementOrder) {
			visitor.valueMap();
			for (String key : elementOrder) {
				CEntry value = properties.get(key);
				if (value != null) {
					String comment = comments.get(key);
					if (comment != null) visitor.comment(comment);
					visitor.key(key);
					value.accept(visitor);
				}
			}
			visitor.pop();
		}

		public boolean isCommentable() {return true;}
		public CMap toCommentable() {return this;}
		public String getComment(String key) {return comments.get(key);}
		public void setComment(String key, String val) {comments.put(key, val);}
		public void clearComments() {comments.clear();}
	}
}