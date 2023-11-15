package roj.mildwind.bridge;

import roj.mildwind.JsContext;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsNull;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;
import roj.mildwind.util.ScriptException;
import roj.text.TextUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:01
 */
public final class JsJavaArray implements JsObject {
	private final Object arr;

	private final int type;
	private final Class<?> objectType;

	private final int length;
	private final long base, scale;

	public JsJavaArray(Object arr) {
		this.arr = arr;
		type = FieldInfo.simpleType(arr.getClass());
		objectType = arr.getClass().getComponentType();
		length = Array.getLength(arr);

		switch (type) {
			case 0: base = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET; scale = Unsafe.ARRAY_BOOLEAN_INDEX_SCALE; break;
			case 1: base = Unsafe.ARRAY_BYTE_BASE_OFFSET;    scale = Unsafe.ARRAY_BYTE_INDEX_SCALE; break;
			case 2: base = Unsafe.ARRAY_SHORT_BASE_OFFSET;   scale = Unsafe.ARRAY_SHORT_INDEX_SCALE; break;
			case 3: base = Unsafe.ARRAY_CHAR_BASE_OFFSET;    scale = Unsafe.ARRAY_CHAR_INDEX_SCALE; break;
			case 4: base = Unsafe.ARRAY_INT_BASE_OFFSET;     scale = Unsafe.ARRAY_INT_INDEX_SCALE; break;
			case 5: base = Unsafe.ARRAY_LONG_BASE_OFFSET;    scale = Unsafe.ARRAY_LONG_INDEX_SCALE; break;
			case 6: base = Unsafe.ARRAY_FLOAT_BASE_OFFSET;   scale = Unsafe.ARRAY_FLOAT_INDEX_SCALE; break;
			case 7: base = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;  scale = Unsafe.ARRAY_DOUBLE_INDEX_SCALE; break;
			default:base = Unsafe.ARRAY_OBJECT_BASE_OFFSET;  scale = Unsafe.ARRAY_OBJECT_INDEX_SCALE; break;
		}
	}

	public Type type() { return Type.ARRAY; }
	public String toString() { return arr.toString(); }

	public JsObject __proto__() { return JsContext.context().ARRAY_PROTOTYPE; }

	public JsObject get(String name) {
		if (name.equals("__proto__")) return JsContext.context().ARRAY_PROTOTYPE;

		return name.equals("length") ? JsContext.getInt(length) : JsNull.UNDEFINED;
	}
	public void put(String name, JsObject value) {
		if (TextUtil.isNumber(name, TextUtil.INT_MAXS) == 0) {
			putByInt(Integer.parseInt(name), value);
		}
	}
	public boolean del(String name) {
		if (name.equals("length")) return false;
		if (TextUtil.isNumber(name, TextUtil.INT_MAXS) != 0) return true;
		int v = Integer.parseInt(name);
		return v < 0 || v >= length;
	}

	public JsObject getByInt(int i) {
		if (i < 0 || i >= length) return JsNull.UNDEFINED;

		long off = base+scale*i;
		switch (type) {
			case 0: return u.getBoolean(arr, off) ? JsBool.TRUE : JsBool.FALSE;
			case 1: return JsContext.getInt(u.getByte(arr, off));
			case 2: return JsContext.getInt(u.getShort(arr, off));
			case 3: return JsContext.getInt(u.getChar(arr, off));
			case 4: return JsContext.getInt(u.getInt(arr, off));
			case 5: return JsContext.getDouble(u.getLong(arr, off));
			case 6: return JsContext.getDouble(u.getFloat(arr, off));
			case 7: return JsContext.getDouble(u.getDouble(arr, off));
			default:
				Object o = u.getObject(arr, off);
				return o == null ? JsNull.NULL : type == 9 ? new JsJavaObject(o) : JsContext.getStr(o.toString());
		}
	}
	public void putByInt(int i, JsObject value) {
		if (i < 0 || i >= length) return;

		long off = base+scale*i;
		switch (type) {
			case 0: u.putBoolean(arr, off, value.asBool()!=0); break;
			case 1: u.putByte(arr, off, (byte) value.asInt()); break;
			case 2: u.putShort(arr, off, (short) value.asInt()); break;
			case 3: u.putChar(arr, off, (char) value.asInt()); break;
			case 4: u.putInt(arr, off, value.asInt()); break;
			case 5: u.putLong(arr, off, (long) value.asDouble()); break;
			case 6: u.putFloat(arr, off, (float) value.asDouble()); break;
			case 7: u.putDouble(arr, off, value.asDouble()); break;
			default:u.putObject(arr, off, value instanceof JsNull ? null : type == 9 ? value.asObject(objectType) : value.toString()); break;
		}
	}
	public boolean delByInt(int i) { return i < 0 || i >= length; }

	public boolean op_in(JsObject toFind) { return !del(toFind.toString()); }
	public boolean op_instanceof(JsObject object) { throw new ScriptException("Right-hand side of 'instanceof' is not callable"); }

	public Iterator<JsObject> _keyItr() {
		return new Iterator<JsObject>() {
			int i = 0;
			public boolean hasNext() { return i < length; }
			public JsObject next() { if (i >= length) throw new NoSuchElementException(); return JsContext.getInt(i++); }
		};
	}
	public Iterator<JsObject> _valItr() {
		return new Iterator<JsObject>() {
			int i = 0;
			public boolean hasNext() { return i < length; }
			public JsObject next() { if (i >= length) throw new NoSuchElementException(); return getByInt(i++); }
		};
	}
}
