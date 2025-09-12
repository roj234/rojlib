package roj.config.node;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.config.TomlParser;
import roj.config.ValueEmitter;
import roj.text.CharList;
import roj.text.Tokenizer;
import roj.util.Helpers;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class MapValue extends ConfigValue {
	/**
	 * 部分配置格式，例如TOML的顶层元素的名称
	 */
	public static final String CONFIG_TOPLEVEL = "<root>";

	protected Map<String, ConfigValue> properties;
	boolean dot;

	public MapValue() { properties = new HashMap<>(); }
	public MapValue(int initialCapacity) { properties = new HashMap<>(initialCapacity); }
	public MapValue(Map<String, ConfigValue> properties) { this.properties = properties; }

	public Type getType() { return Type.MAP; }

	public MapValue asMap() { return this; }

	@Override
	public void accept(ValueEmitter visitor) {
		visitor.emitMap(properties.size());
		if (!properties.isEmpty()) {
			for (var entry : properties.entrySet()) {
				visitor.key(entry.getKey());
				entry.getValue().accept(visitor);
			}
		}
		visitor.pop();
	}
	public void accept(ValueEmitter visitor, String... elementOrder) {
		visitor.emitMap();
		for (String name : elementOrder) {
			ConfigValue value = properties.get(name);
			if (value != null) {
				visitor.key(name);
				value.accept(visitor);
			}
		}
		visitor.pop();
	}

	public final Map<String, ConfigValue> raw() { return properties; }
	public final Map<String, Object> unwrap() {
		HashMap<String, Object> rawdata = Helpers.cast(properties instanceof LinkedHashMap ? new LinkedHashMap<>(properties) : new HashMap<>(properties));
		for (var entry : rawdata.entrySet()) {
			entry.setValue(((ConfigValue) entry.getValue()).unwrap());
		}
		return rawdata;
	}

	public final Set<Map.Entry<String, ConfigValue>> entrySet() { return properties.entrySet(); }
	public final Set<String> keySet() { return properties.keySet(); }
	public final Collection<ConfigValue> values() { return properties.values(); }

	public boolean isEmpty() {return properties.isEmpty();}
	public final int size() {return properties.size();}
	public void dot(boolean dotMode) {this.dot = dotMode;}

	// region PUT
	public final ConfigValue put(String key, ConfigValue entry) { return put(key, entry == null ? NullValue.NULL : entry, 0); }
	public final ConfigValue put(String key, @NotNull String entry) {
		Objects.requireNonNull(entry);

		ConfigValue prev = get(key);
		if (prev.getType() == Type.STRING) {
			((StringValue) prev).value = entry;
			return null;
		} else {
			return put(key, ConfigValue.valueOf(entry), 0);
		}
	}
	public final ConfigValue put(String key, boolean entry) { return put(key, ConfigValue.valueOf(entry), 0); }
	public final ConfigValue put(String key, int entry) {
		ConfigValue prev = get(key);
		if (prev.getType() == Type.INTEGER) {
			((IntValue) prev).value = entry;
			return null;
		} else {
			return put(key, ConfigValue.valueOf(entry), 0);
		}
	}
	public final ConfigValue put(String key, long entry) {
		ConfigValue prev = get(key);
		if (prev.getType() == Type.LONG) {
			((LongValue) prev).value = entry;
			return null;
		} else {
			return put(key, ConfigValue.valueOf(entry), 0);
		}
	}
	public final ConfigValue put(String key, double entry) {
		ConfigValue prev = get(key);
		if (prev.getType() == Type.DOUBLE) {
			((DoubleValue) prev).value = entry;
			return null;
		} else {
			return put(key, ConfigValue.valueOf(entry), 0);
		}
	}

	public final ConfigValue putIfAbsent(String key, ConfigValue v) { return put(key, v, Q_SET_IF_ABSENT);}
	public final String putIfAbsent(String key, String v) { return put(key, ConfigValue.valueOf(v), Q_SET_IF_ABSENT).asString();}
	public final boolean putIfAbsent(String key, boolean v) { return put(key, ConfigValue.valueOf(v), Q_SET_IF_ABSENT).asBool();}
	public final int putIfAbsent(String key, int v) { return put(key, ConfigValue.valueOf(v), Q_SET_IF_ABSENT).asInt();}
	public final long putIfAbsent(String key, long v) { return put(key, ConfigValue.valueOf(v), Q_SET_IF_ABSENT).asLong();}
	public final double putIfAbsent(String key, double v) { return put(key, ConfigValue.valueOf(v), Q_SET_IF_ABSENT).asDouble();}

	public final MapValue getOrCreateMap(String key) { return put(key, new MapValue(), Q_SET_IF_ABSENT).asMap();}
	public final ListValue getOrCreateList(String key) { return put(key, new ListValue(), Q_SET_IF_ABSENT).asList();}

	protected ConfigValue put(String k, ConfigValue v, int flag) {
		if (!dot) {
			if ((flag & Q_SET_IF_ABSENT) == 0 || !properties.getOrDefault(k, NullValue.NULL).mayCastTo(v.getType())) {
				properties.put(k, v);
				return v;
			}
			return properties.getOrDefault(k, v);
		}

		return update(k, Q_CREATE_MID|Q_SET|flag, v);
	}
	// endregion
	// region GET

	public final boolean containsKey(String key) {return get(key, null) != null;}
	public final boolean containsKey(String key, Type type) {return get(key).mayCastTo(type);}

	public final boolean getBool(String key) {return getBool(key, false);}
	public final boolean getBool(String key, boolean def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.BOOL) ? entry.asBool() : def;
	}
	@NotNull
	public final String getString(String key) {return getString(key, "");}
	@Contract("_,!null -> !null")
	public final String getString(String key, String def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : def;
	}
	public final int getInteger(String key) {return getInt(key, 0);}
	public final int getInteger(String key, int def) {return getInt(key, def);}
	public final int getInt(String key) {return getInt(key, 0);}
	public final int getInt(String key, int def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.INTEGER) ? entry.asInt() : def;
	}
	public final long getLong(String key) {return getLong(key, 0);}
	public final long getLong(String key, long def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.LONG) ? entry.asLong() : def;
	}
	public final float getFloat(String key) {return getFloat(key, 0);}
	public final float getFloat(String key, float def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.Float4) ? entry.asFloat() : def;
	}
	public final double getDouble(String key) {return getDouble(key, 0);}
	public final double getDouble(String key, double def) {
		ConfigValue entry = get(key);
		return entry.mayCastTo(Type.DOUBLE) ? entry.asDouble() : def;
	}
	@NotNull public ListValue getList(String key) {return get(key).asList();}
	@NotNull public MapValue getMap(String key) {return get(key).asMap();}

	@NotNull public ConfigValue get(String key) {return get(key, NullValue.NULL);}
	@Nullable public final ConfigValue getNullable(String k) {return get(k,null);}
	@Contract("_,!null -> !null") public ConfigValue get(String k, ConfigValue def) {
		if (k == null) return def;
		return !dot ? properties.getOrDefault(k, def) : query(k);
	}
	// endregion
	public final void remove(String name) { properties.remove(name); }

	////// comments

	public boolean isCommentable() {return false;}
	public MapValue toCommentable() {return new Commentable();}
	public String getComment(String key) {return null;}
	public void setComment(String key, String val) {throw new UnsupportedOperationException();}
	public void clearComments() {}

	/**
	 * 使自身符合rule中定义的规则（字段名称，类型，数量）
	 * 多出的将被删除（可选）
	 */
	public final void format(MapValue rule, boolean deep, boolean remove) {
		Map<String, ConfigValue> map = this.properties;
		for (var entry : rule.properties.entrySet()) {
			String k = entry.getKey();

			ConfigValue myEnt = map.putIfAbsent(entry.getKey(), entry.getValue());
			if (myEnt == null) continue;
			ConfigValue oEnt = entry.getValue();

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
	public void merge(MapValue o, boolean self, boolean deep) {
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
					ConfigValue oEnt = entry.getValue();
					ConfigValue myEnt = properties.putIfAbsent(entry.getKey(), oEnt);
					if (myEnt != null) {
						if (myEnt.getType() == Type.MAP && oEnt.getType() == Type.MAP) {
							myEnt.asMap().merge(oEnt.asMap(), true, true);
						}
					}
				}
			} else {
				for (var entry : o.properties.entrySet()) {
					ConfigValue oEnt = entry.getValue();
					ConfigValue myEnt = properties.put(entry.getKey(), oEnt);
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
				if (TomlParser.literalSafe(chain)) {
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

				ConfigValue v = entry.getValue();
				switch (v.getType()) {
					case MAP:
						if (entry.getKey().equals(CONFIG_TOPLEVEL) && depth == 0) continue;
						v.toTOML(sb, depth == 3 ? 3 : 1, entry.getKey()).append('\n');
						break;
					case LIST:
						v.toTOML(sb, depth == 3 ? 3 : 0, entry.getKey()).append('\n');
						break;
					default:
						if (TomlParser.literalSafe(entry.getKey())) {
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
		if (!(o instanceof MapValue mapping)) return false;
		return properties.equals(mapping.properties);
	}

	public static class Commentable extends MapValue {
		public Map<String, String> comments = new HashMap<>();

		public Commentable() {}
		public Commentable(Map<String, ConfigValue> properties) { super(properties); }
		public Commentable(Map<String, ConfigValue> properties, Map<String, String> comments) {
			super(properties);
			this.comments = comments;
		}

		@Override
		public void accept(ValueEmitter visitor) {
			visitor.emitMap();
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

		public void accept(ValueEmitter visitor, String... elementOrder) {
			visitor.emitMap();
			for (String key : elementOrder) {
				ConfigValue value = properties.get(key);
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
		public MapValue toCommentable() {return this;}
		public String getComment(String key) {return comments.get(key);}
		public void setComment(String key, String val) {comments.put(key, val);}
		public void clearComments() {comments.clear();}
	}
}