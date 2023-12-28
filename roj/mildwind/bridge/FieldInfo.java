package roj.mildwind.bridge;

import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.mildwind.JsContext;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsNull;
import roj.mildwind.type.JsObject;

import java.lang.reflect.Field;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:05
 */
final class FieldInfo {
	private final int type;
	private final long off;
	private final Class<?> objectType;

	FieldInfo(Field field) {
		this.objectType = field.getType();
		this.off = (field.getModifiers()&Opcodes.ACC_STATIC) == 0 ? u.objectFieldOffset(field) : u.staticFieldOffset(field);
		this.type = getType(objectType);
	}

	public static int getType(Class<?> klass) {
		switch (TypeHelper.class2type(klass).type) {
			case Type.BOOLEAN: return 0;
			case Type.BYTE: return 1;
			case Type.CHAR: return 2;
			case Type.SHORT: return 3;
			case Type.INT: return 4;
			case Type.LONG: return 5;
			case Type.FLOAT: return 6;
			case Type.DOUBLE: return 7;
			default: // no default actually
			case Type.CLASS: return klass.isAssignableFrom(CharSequence.class) ? 8 : 9;
		}
	}

	public final JsObject get(Object obj) {
		switch (type) {
			case 0: return u.getBoolean(obj, off) ? JsBool.TRUE : JsBool.FALSE;
			case 1: return JsContext.getInt(u.getByte(obj, off));
			case 2: return JsContext.getInt(u.getShort(obj, off));
			case 3: return JsContext.getInt(u.getChar(obj, off));
			case 4: return JsContext.getInt(u.getInt(obj, off));
			case 5: return JsContext.getDouble(u.getLong(obj, off));
			case 6: return JsContext.getDouble(u.getFloat(obj, off));
			case 7: return JsContext.getDouble(u.getDouble(obj, off));
			default:
				Object o = u.getObject(obj, off);
				return o == null ? JsNull.NULL : type == 9 ? new JsJavaObject(o) : JsContext.getStr(o.toString());
		}
	}
	public final void set(Object obj, JsObject value) {
		switch (type) {
			case 0: u.putBoolean(obj, off, value.asBool()!=0); break;
			case 1: u.putByte(obj, off, (byte) value.asInt()); break;
			case 2: u.putShort(obj, off, (short) value.asInt()); break;
			case 3: u.putChar(obj, off, (char) value.asInt()); break;
			case 4: u.putInt(obj, off, value.asInt()); break;
			case 5: u.putLong(obj, off, (long) value.asDouble()); break;
			case 6: u.putFloat(obj, off, (float) value.asDouble()); break;
			case 7: u.putDouble(obj, off, value.asDouble()); break;
			default:u.putObject(obj, off, value instanceof JsNull ? null : type == 9 ? value.asObject(objectType) : value.toString()); break;
		}
	}
}