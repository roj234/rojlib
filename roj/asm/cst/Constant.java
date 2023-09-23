package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class Constant implements Cloneable {
	public static final byte
		UTF = 1,
		_TOP_ = 2,
		INT = 3,
		FLOAT = 4,
		LONG = 5,
		DOUBLE = 6,
		CLASS = 7,
		STRING = 8,
		FIELD = 9,
		METHOD = 10,
		INTERFACE = 11,
		NAME_AND_TYPE = 12,
		METHOD_HANDLE = 15,
		METHOD_TYPE = 16,
		DYNAMIC = 17,
		INVOKE_DYNAMIC = 18,
		MODULE = 19,
		PACKAGE = 20;

	private static final String[] indexes = {
		"UTF", null, "int", "float", "long", "double", "类", "String",
		"字段", "方法", "接口", "DESC", null, null,
		"METHOD_HANDLE", "METHOD_TYPE", "DYNAMIC", "INVOKE_DYNAMIC",
		"模块", "包"
	};
	public static String toString(int id) { return id < 1 || id > 20 ? null : indexes[id-1]; }

	private char index;

	Constant() {}

	abstract void write(DynByteBuf w);

	public abstract boolean equals(Object o);
	public abstract int hashCode();

	public String toString() { return toString(type()) + "#" + (int) index; }
	public String getEasyReadValue() { throw new UnsupportedOperationException(); }
	public String getEasyCompareValue() { throw new UnsupportedOperationException(); }

	final void setIndex(int i) {
		this.index = (char) i;
	}
	public final int getIndex() { return index; }

	public abstract byte type();

	@Override
	public Constant clone() {
		try {
			return (Constant) super.clone();
		} catch (CloneNotSupportedException unable) {}
		return null;
	}
}