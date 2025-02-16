package roj.config.data;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.config.ConfigMaster;
import roj.config.serial.CVisitor;
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
public abstract class CEntry {
	public static CEntry valueOf(String v) {return new CString(v);}
	public static CEntry valueOf(boolean v) {return v ? CBoolean.TRUE : CBoolean.FALSE;}
	public static CEntry valueOf(byte v) {return new CByte(v);}
	public static CEntry valueOf(short v) {return new CShort(v);}
	public static CEntry valueOf(char v) {return new CChar(v);}
	public static CEntry valueOf(int v) {return new CInt(v);}
	public static CEntry valueOf(long v) {return new CLong(v);}
	public static CEntry valueOf(float v) {return new CFloat(v);}
	public static CEntry valueOf(double v) {return new CDouble(v);}

	protected CEntry() {}

	public abstract Type getType();
	public char dataType() {return getType().symbol();}
	public boolean contentEquals(CEntry o) {
		return this == o || (o.getType().ordinal() > Type.INTEGER.ordinal()
		? o.contentEquals(this)
		: o.mayCastTo(getType()) && eqVal(o));
	}
	protected boolean eqVal(CEntry o) { return equals(o); }
	public boolean mayCastTo(Type o) { return o == getType(); }

	public static final int Q_SET = 1, Q_SET_IF_ABSENT = 2, Q_SET_IF_NOT_SIMILAR = 4, Q_CREATE_MID = 8, Q_REPLACE_MID = 16, Q_RETURN_CONTAINER = 32;
	public final CEntry query(CharSequence sql) { return query(sql, 0, null); }
	@Contract("_, _, !null -> !null")
	public final CEntry query(CharSequence sql, @MagicConstant(flags = {Q_SET,Q_SET_IF_ABSENT,Q_SET_IF_NOT_SIMILAR,Q_CREATE_MID,Q_REPLACE_MID,Q_RETURN_CONTAINER}) int flag, @Nullable CEntry def) {
		CEntry node = this;

		var tmp = IOUtil.getSharedCharBuf();
		boolean quoted = false;

		int prevI = 0;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			switch (c) {
				case '"': quoted ^= true; continue;
				case '.': case '[': if (quoted) break;
					if (sql.charAt(prevI) != ']') {
						if (node.getType() != Type.MAP) throw new IllegalStateException("期待映射 '"+sql.subSequence(0, prevI)+"' 实际为 "+node.getType());

						node = getInMap(node, tmp, flag, c=='[');
						tmp.clear();
						if (node == null) return def;
					}

					if (c == '[') {
						if (node.getType() != Type.LIST) throw new IllegalStateException("期待列表 '"+sql.subSequence(0, i)+"' 实际为 "+node.getType());

						int j = ++i;
						foundEnd: {
							for (; j < sql.length(); j++) {
								if (sql.charAt(j) == ']') break foundEnd;
							}
							throw new IllegalArgumentException("invalid ListEq: not end: "+sql);
						}

						if (j==i) throw new IllegalArgumentException("invalid ListEq: not number: "+sql);
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
									} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && entry.contentEquals(def)) {
										break set;
									}

									list.set(pos, def);
								}

								return entry;
							}
						} else if ((c=sql.charAt(j+1)) != '.' && c != '[') {
							throw new IllegalArgumentException("invalid ListEq: excepting '.' or '[' at "+(j+1)+": "+sql);
						} else if (c == '.') j++;

						node = node.asList().get(pos);
						i = j;
					}
					prevI = i;
					continue;
			}
			tmp.append(c);
		}

		if (node.getType() != Type.MAP) throw new IllegalStateException("match failed: '"+sql.subSequence(0, prevI)+"' is not a MAP but "+node.getType());

		if ((flag & Q_RETURN_CONTAINER) != 0) return node;

		Map<String, CEntry> map = node.asMap().raw();
		if ((flag & Q_SET) == 0) return map.getOrDefault(tmp, def);

		if (def == null) return map.remove(tmp);

		CEntry v = map.getOrDefault(tmp, CNull.NULL);
		if ((flag & Q_SET_IF_ABSENT) != 0 && v != CNull.NULL) {
			return v;
		} else if ((flag & Q_SET_IF_NOT_SIMILAR) != 0 && v.contentEquals(def)) {
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
				_map.put(name.toString(), node = new CMap());
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
	public CMap asMap() { throw new ClassCastException(getType()+"不是映射"); }
	public CList asList() { throw new ClassCastException(getType()+"不是列表"); }

	////// toString methods

	@Override
	public String toString() { return ConfigMaster.JSON.toString(this); }

	public abstract void accept(CVisitor ser);
	/**
	 * 获取这个CEntry的内部表示
	 * * 对于非基本类型来说，不会创建新对象
	 * @see #unwrap()
	 * @return
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

	protected CharList toJSON(CharList sb, int depth) {throw new NoSuchMethodError();}

	//region 未使用，预留
	public CEntry __call(CEntry self, CEntry args) {throw new UnsupportedOperationException(getClass()+"不是函数");}
	public CEntry __getattr(String name) {return null;}
	public void __setattr(String name, CEntry value) {}
	//endregion
}