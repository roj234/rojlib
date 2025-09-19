package roj.asm.cp;

import roj.asm.type.Type;
import roj.util.DynByteBuf;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract sealed class Constant implements Cloneable permits CstDouble, CstDynamic, CstFloat, CstInt, CstLong, CstMethodHandle, CstNameAndType, CstRef, CstRefUTF, CstTop, CstUTF {
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

	private static final String[] NAMES = {
		"字符串", null, "int", "float", "long", "double", "Class", "String",
		"字段", "方法", "接口", "名称和参数", null, null,
		"MethodHandle", "MethodType", "动态常量", "动态方法",
		"模块", "包"
	};
	static final Type[] TYPES = {
			null, null, INT_TYPE, FLOAT_TYPE, LONG_TYPE, DOUBLE_TYPE, klass("java/lang/Class"), klass("java/lang/String"),
			null, null, null, null, klass("java/lang/invoke/MethodHandle"), klass("java/lang/invoke/MethodType"),
			null, null, null, null
	};

	public static String toString(int id) { return id < 1 || id > 20 ? null : NAMES[id-1]; }

	char index;

	Constant() {}

	public abstract byte type();
	abstract void write(DynByteBuf w);

	public Type resolvedType() {
		Type type = TYPES[type()];
		if (type == null) throw new IllegalArgumentException("Constant "+this+" is not loadable");
		return type;
	}

	public String toString() {return toString(type())+"#"+(int) index;}
	public String getEasyCompareValue() {throw new UnsupportedOperationException();}

	public abstract boolean equals(Object o);
	public abstract int hashCode();

	@Override
	public Constant clone() {
		try {
			return (Constant) super.clone();
		} catch (CloneNotSupportedException ignored) {}
		return null;
	}
}