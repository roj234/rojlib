package roj.asm.type;

import roj.concurrent.OperationDone;
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

	private static final Object[][] MAP = new Object[26][];
	static {
		A(ARRAY, "[", "array", 4);
		A(CLASS, "L", "class", 4);
		A(VOID, "V", "void", 5);
		A(BOOLEAN, "Z", "boolean", 0);
		A(BYTE, "B", "byte", 0);
		A(CHAR, "C", "char", 0);
		A(SHORT, "S", "short", 0);
		A(INT, "I", "int", 0);
		A(FLOAT, "F", "float", 2);
		A(DOUBLE, "D", "double", 3);
		A(LONG, "J", "long", 1);
	}
	private static void A(char c, String str, String desc, int off) {
		MAP[c-BYTE] = new Object[] {str, desc, new Type((byte)c, false), off};
	}

	public static String toDesc(int type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[0].toString();
	}

	public static String toString(byte type) {
		if (type < BYTE || type > ARRAY) return Helpers.nonnull();
		Object[] arr = MAP[type-BYTE];
		if (arr == null) return Helpers.nonnull();
		return arr[1].toString();
	}

	public static boolean isValid(int c) {
		if (c < BYTE || c > ARRAY) return false;
		return MAP[c-BYTE] != null;
	}

	public static byte validate(int c) {
		if (c >= BYTE && c <= ARRAY) {
			Object[] arr = MAP[c-BYTE];
			if (arr != null) return (byte) c;
		}
		throw new IllegalArgumentException("Illegal type desc " + (char)c);
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

	public Type(char type) {
		this(type, 0);
	}

	/**
	 * TYPE_OTHER
	 */
	public Type(int type, int array) {
		this.type = validate(type);
		if (type == ARRAY) throw new IllegalStateException("Array type is only for compute");
		setArrayDim(array);
	}

	public Type(String type) {
		this(type, 0);
	}

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
		if (this.owner != null) {
			sb.append(owner);
		} else {
			sb.append(toString(type));
		}
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
	public String owner() {
		if (owner == null) throw new IllegalStateException("Kind is not class");
		return owner;
	}

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
		if (array != 0) return (byte) (4+code);
		int v = (int) MAP[type-BYTE][3];
		if (v == 5 && !allowVoid) throw new IllegalStateException("VOID is not allowed");
		return (byte) (v+code);
	}

	@Deprecated
	public String nativeName() {
		switch (type) {
			case CLASS:
				return "A";
			case VOID:
				return "";
			case BOOLEAN:
			case BYTE:
			case CHAR:
			case SHORT:
			case INT:
				return "I";
			case FLOAT:
				return "F";
			case DOUBLE:
				return "D";
			case LONG:
				return "L";
		}
		throw OperationDone.NEVER;
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
