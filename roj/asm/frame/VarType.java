package roj.asm.frame;

import roj.asm.type.Type;
import roj.concurrent.OperationDone;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class VarType {
	public static final byte TOP = 0, INT = 1, FLOAT = 2, DOUBLE = 3, LONG = 4, NULL = 5, UNINITIAL_THIS = 6, REFERENCE = 7, UNINITIAL = 8, ANY = 114, ANY2 = 115;

	public static int ofType(Type type) {
		if (type.array() > 0) return -2;
		switch (type.type) {
			case Type.VOID:
				return -1;
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.CHAR:
			case Type.SHORT:
			case Type.INT:
				return INT;
			case Type.FLOAT:
				return FLOAT;
			case Type.DOUBLE:
				return DOUBLE;
			case Type.LONG:
				return LONG;
			case Type.CLASS:
				return -2;
		}
		throw OperationDone.NEVER;
	}

	static final String[] toString = {"top", "int", "long", "float", "double", "{null}", "uninitial_this", "object", "uninitial"};

	public static String toString(byte type) {
		return toString[type];
	}

	public static int validate(int b) {
		if ((b &= 0xFF) > 8) {
			throw new IllegalArgumentException("Unsupported verification type " + b);
		}
		return b;
	}

	public static boolean isPrimitive(byte type) {
		return type <= 4;
	}
}
