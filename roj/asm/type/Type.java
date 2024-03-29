package roj.asm.type;

import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.function.UnaryOperator;

/**
 * 类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Type implements IType {
	public static final char ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

	static final Object[][] MAP = new Object[26][];
	static {
		A(ARRAY, "[", null, null, 4);
		A(CLASS, "L", "object", "A", 4);
		A(VOID, "V", "void", null, 5);
		A(BOOLEAN, "Z", "boolean", "I", 0);
		A(BYTE, "B", "byte", "I", 0);
		A(CHAR, "C", "char", "I", 0);
		A(SHORT, "S", "short", "I", 0);
		A(INT, "I", "int", "I", 0);
		A(FLOAT, "F", "float", "F", 2);
		A(DOUBLE, "D", "double", "D", 3);
		A(LONG, "J", "long", "L", 1);
	}
	private static void A(char c, String desc, String name, String opName, int off) {
		MAP[c-BYTE] = new Object[] {desc, name, new Type((byte)c, false), off, opName};
	}

	public static String toDesc(int type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[0].toString();
	}
	public static String toString(int type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[1].toString();
	}

	public static boolean isValid(int c) {
		if (c < BYTE || c > ARRAY) return false;
		return MAP[c-BYTE] != null;
	}

	public static Type std(int c) {
		if (c >= BYTE && c <= ARRAY) {
			Object[] arr = MAP[c-BYTE];
			if (arr != null) return (Type) arr[2];
		}
		throw new IllegalArgumentException("Illegal type desc " + (char)c);
	}

	/**
	 * Array正常不会出现
	 */
	public final byte type;
	public String owner;
	private byte array;

	private Type(byte c, boolean _unused) {
		type = c;
	}

	public Type(char type) { this(type, 0); }
	/**
	 * TYPE_OTHER
	 */
	public Type(int type, int array) {
		if (!isValid(type)) throw new IllegalArgumentException("Not valid type: " + type);
		if (type == ARRAY) throw new IllegalArgumentException("Array type is only for switch");
		if (type == CLASS) throw new IllegalArgumentException("Owner cannot be null");

		this.type = (byte) type;
		setArrayDim(array);
	}

	public Type(String type) { this(type, 0); }
	/**
	 * TYPE_CLASS
	 */
	public Type(String owner, int array) {
		this.type = CLASS;
		this.owner = owner;
		setArrayDim(array);
	}

	@Override
	public void toDesc(CharList sb) {
		for (int i = array&0xFF; i > 0; i--) sb.append('[');
		sb.append((char) type);
		if (type == Type.CLASS) sb.append(owner).append(';');
	}

	@Override
	public void toString(CharList sb) {
		sb.append(this.owner != null ? owner : toString(type));
		for (int i = array&0xFF; i > 0; i--) sb.append("[]");
	}

	@Override
	public byte genericType() {
		return STANDARD_TYPE;
	}

	@Override
	public Type rawType() {
		return this;
	}

	@Override
	public String owner() { return owner; }

	@Override
	public void owner(String owner) {
		if (this.owner == null || owner == null) throw new IllegalStateException("Kind is not class");
		this.owner = owner;
	}

	@Override
	public void rename(UnaryOperator<String> fn) {
		if (owner != null) owner = fn.apply(owner);
	}

	@Override
	public void checkPosition(int env, int pos) {
		switch (env) {
			case FIELD_ENV:
			case INPUT_ENV:
				if (type == 'V')
					throw new IllegalStateException("field or input cannot be 'void' type");
				break;
			case THROW_ENV:
				if (type != 'L' || array != 0)
					throw new IllegalStateException(this + " is not throwable");
		}
	}

	public int length() {
		return type == VOID ? 0 : (array == 0 && (type == LONG || type == DOUBLE)) ? 2 : 1;
	}

	public byte shiftedOpcode(int code, boolean allowVoid) {
		if (type == VOID && !allowVoid) throw new IllegalStateException("VOID is not allowed");
		return (byte) ((int) MAP[getActualType()-BYTE][3]+code);
	}

	public String nativeName() {
		return MAP[getActualType()-BYTE][4].toString();
	}
	public boolean isPrimitive() {
		return array == 0 && type != CLASS;
	}
	public int getActualType() {
		return array == 0 ? type : CLASS;
	}
	public String getActualClass() {
		return array == 0 ? owner : toDesc();
	}

	public String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		toString(sb);
		return sb.toString();
	}

	public Class<?> toJavaClass() throws ClassNotFoundException {
		if (type == CLASS || array != 0) {
			String cn;
			if (type == CLASS && array == 0) {
				cn = owner.replace('/', '.');
			} else {
				CharList sb = IOUtil.getSharedCharBuf();
				toDesc(sb);
				cn = sb.replace('/', '.').toString();
			}

			try {
				return Class.forName(cn, false, null);
			} catch (Exception e) {
				return Class.forName(cn, false, Type.class.getClassLoader());
			}
		}

		switch (type) {
			case VOID: return void.class;
			case BOOLEAN: return boolean.class;
			case BYTE: return byte.class;
			case CHAR: return char.class;
			case SHORT: return short.class;
			case INT: return int.class;
			case FLOAT: return float.class;
			case DOUBLE: return double.class;
			case LONG: return long.class;
		}
		throw new IllegalArgumentException("?");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Type type1 = (Type) o;

		if (type != type1.type) return false;
		if (array != type1.array) return false;
		return type != CLASS || owner.equals(type1.owner);
	}

	@Override
	public int hashCode() {
		int result = type;
		result = 31 * result + (owner != null ? owner.hashCode() : 0);
		result = 31 * result + array;
		return result;
	}

	@Override
	public int array() {
		return array&0xFF;
	}

	@Override
	public void setArrayDim(int array) {
		if (array > 255 || array < 0) throw new ArrayIndexOutOfBoundsException(array);
		if (type == VOID && array != 0) throw new IllegalStateException("VOID cannot have array");
		this.array = (byte) array;
	}
}
