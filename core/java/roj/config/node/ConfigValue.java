package roj.config.node;

import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.config.ConfigMaster;
import roj.config.ValueEmitter;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.List;
import java.util.Map;

/**
 * Config Entry
 *
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public abstract class ConfigValue {
	public static ConfigValue valueOf(String v) {return new StringValue(v);}
	public static ConfigValue valueOf(boolean v) {return v ? BoolValue.TRUE : BoolValue.FALSE;}
	public static ConfigValue valueOf(byte v) {return new ByteValue(v);}
	public static ConfigValue valueOf(short v) {return new ShortValue(v);}
	public static ConfigValue valueOf(char v) {return new CharValue(v);}
	public static ConfigValue valueOf(int v) {return new IntValue(v);}
	public static ConfigValue valueOf(long v) {return new LongValue(v);}
	public static ConfigValue valueOf(float v) {return new FloatValue(v);}
	public static ConfigValue valueOf(double v) {return new DoubleValue(v);}

	protected ConfigValue() {}

	public abstract Type getType();
	public char dataType() {return getType().symbol();}
	public boolean contentEquals(ConfigValue o) {
		return this == o || (o.getType().ordinal() > Type.INTEGER.ordinal()
		? o.contentEquals(this)
		: o.mayCastTo(getType()) && eqVal(o));
	}
	protected boolean eqVal(ConfigValue o) { return equals(o); }
	public boolean mayCastTo(Type o) { return o == getType(); }

	public static final int Q_SET = 1, Q_SET_IF_ABSENT = 2, Q_SET_IF_NOT_SIMILAR = 4, Q_CREATE_MID = 8, Q_REPLACE_MID = 16, Q_RETURN_CONTAINER = 32;
	public final ConfigValue query(@Language("JSONPath") String jsonPath) { return update(jsonPath, 0, NullValue.NULL); }
	@Contract("_, _, !null -> !null")
	public final ConfigValue update(@Language("JSONPath") String jsonPath,
									@MagicConstant(flags = {Q_SET,Q_SET_IF_ABSENT,Q_SET_IF_NOT_SIMILAR,Q_CREATE_MID,Q_REPLACE_MID,Q_RETURN_CONTAINER}) int flag,
									@Nullable ConfigValue def) {
		ConfigValue node = this;

		var tmp = IOUtil.getSharedCharBuf();
		boolean quoted = false;

		int prevI = 0;
		for (int i = 0; i < jsonPath.length(); i++) {
			char c = jsonPath.charAt(i);
			switch (c) {
				case '"': quoted ^= true; continue;
				case '.': case '[': if (quoted) break;
					if (jsonPath.charAt(prevI) != ']') {
						if (node.getType() != Type.MAP) throw new IllegalStateException("期待映射 '"+jsonPath.substring(0, prevI)+"' 实际为 "+node.getType());

						node = getInMap(node, tmp, flag, c=='[');
						tmp.clear();
						if (node == null) return def;
					}

					if (c == '[') {
						if (node.getType() != Type.LIST) throw new IllegalStateException("期待列表 '"+jsonPath.substring(0, i)+"' 实际为 "+node.getType());

						int j = ++i;
						foundEnd: {
							for (; j < jsonPath.length(); j++) {
								if (jsonPath.charAt(j) == ']') break foundEnd;
							}
							throw new IllegalArgumentException("invalid ListEq: not end: "+jsonPath);
						}

						if (j==i) throw new IllegalArgumentException("invalid ListEq: not number: "+jsonPath);
						if (tmp.length() > 0) throw new AssertionError();
						int pos = TextUtil.parseInt(tmp.append(jsonPath, i, j));
						tmp.clear();

						if (j == jsonPath.length()-1) {
							List<ConfigValue> list = node.asList().raw();
							if (list.size() <= pos) {
								if ((flag & Q_SET) == 0) return def;
								if (def != null) {
									while (list.size() <= pos) list.add(NullValue.NULL);
									list.set(pos, def);
								}

								return null;
							} else {
								ConfigValue entry = node.asList().get(pos);

								set:
								if ((flag & Q_SET) != 0) {
									if (def == null) return list.remove(pos);

									if ((flag & Q_SET_IF_ABSENT) != 0 && entry != NullValue.NULL) {
										break set;
									} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && entry.contentEquals(def)) {
										break set;
									}

									list.set(pos, def);
								}

								return entry;
							}
						} else if ((c=jsonPath.charAt(j+1)) != '.' && c != '[') {
							throw new IllegalArgumentException("invalid ListEq: excepting '.' or '[' at "+(j+1)+": "+jsonPath);
						} else if (c == '.') j++;

						node = node.asList().get(pos);
						i = j;
					}
					prevI = i;
					continue;
			}
			tmp.append(c);
		}

		if (node.getType() != Type.MAP) throw new IllegalStateException("match failed: '"+jsonPath.substring(0, prevI)+"' is not a MAP but "+node.getType());

		if ((flag & Q_RETURN_CONTAINER) != 0) return node;

		Map<String, ConfigValue> map = node.asMap().raw();
		if ((flag & Q_SET) == 0) return map.getOrDefault(tmp, def);

		if (def == null) return map.remove(tmp);

		ConfigValue v = map.getOrDefault(tmp, NullValue.NULL);
		if ((flag & Q_SET_IF_ABSENT) != 0 && v != NullValue.NULL) {
			return v;
		} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && v.contentEquals(def)) {
			return v;
		}

		map.put(tmp.toString(), def);
		return def;
	}
	private static ConfigValue getInMap(ConfigValue node, CharList name, int opFlag, boolean list) {
		Map<String, ConfigValue> _map = node.asMap().raw();
		node = _map.getOrDefault(name, NullValue.NULL);
		// 这里不对错误的type扔异常，sql和pos传进来浪费
		if (node.getType() == Type.NULL || (node.getType() != (list?Type.LIST:Type.MAP) && (opFlag & Q_REPLACE_MID) != 0)) {
			if ((opFlag & Q_CREATE_MID) != 0) {
				if (list) throw new IllegalStateException("Cannot create LIST");
				_map.put(name.toString(), node = new MapValue());
			} else {
				return null;
			}
		}
		return node;
	}

	////// easy caster

	public boolean asBool() { throw new ClassCastException(getType()+"不是布尔值"); }
	@Deprecated public final int asInteger() { return asInt(); }
	public int asInt() { throw new ClassCastException(getType()+"不是整数"); }
	public char asChar() { throw new ClassCastException(getType()+"不是字符"); }
	public long asLong() { throw new ClassCastException(getType()+"不是长整数"); }
	public float asFloat() { throw new ClassCastException(getType()+"不是浮点数"); }
	public double asDouble() { throw new ClassCastException(getType()+"不是双精度浮点数"); }
	public String asString() { throw new ClassCastException(getType()+"不是字符串"); }
	public MapValue asMap() { throw new ClassCastException(getType()+"不是映射"); }
	public ListValue asList() { throw new ClassCastException(getType()+"不是列表"); }

	////// toString methods

	@Override
	public String toString() { return ConfigMaster.JSON.toString(this); }

	public abstract void accept(ValueEmitter visitor);
	/**
	 * 获取这个CEntry的内部表示
	 * * 对于非基本类型来说，不会创建新对象
	 * @see #unwrap()
	 */
	public abstract Object raw();
	/**
	 * 递归式的将这个CEntry转换为Java标准库中的包装类型
	 * * 会创建不少对象
	 * @see #raw()
	 * @return Java包装器表示
	 */
	public Object unwrap() {return raw();}

	protected CharList toTOML(CharList sb, int depth, CharSequence chain) { return toJSON(sb, 0); }
	public final CharList appendTOML(CharList sb, CharList tmp) { return toTOML(sb, 0, tmp); }

	public CharList toJSON(CharList sb, int depth) {throw new NoSuchMethodError();}

	//region 未使用的API，为脚本语言预留
	public ConfigValue __call(ConfigValue self, ConfigValue args) {throw new UnsupportedOperationException(getClass()+"不是函数");}
	public ConfigValue __getattr(String name) {return null;}
	public void __setattr(String name, ConfigValue value) {}
	//endregion
}