package roj.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static roj.reflect.ReflectionUtils.u;

/**
 * 抽象字段访问者
 *
 * @author Roj234
 * @since 2020/10/17 18:24
 */
public final class FieldAccessor {
	private static final String[] arr = "BOOL,BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,OBJECT".split(",");

	public final Field field;
	public final byte flag;

	FieldAccessor(Field field) {
		this.field = field;
		int flag;
		Class<?> type = field.getType();
		if (type.isPrimitive()) {
			switch (type.getName()) {
				case "int":
					flag = 4;
					break;
				case "short":
					flag = 2;
					break;
				case "double":
					flag = 7;
					break;
				case "long":
					flag = 5;
					break;
				case "float":
					flag = 6;
					break;
				case "char":
					flag = 3;
					break;
				case "byte":
					flag = 1;
					break;
				case "boolean":
					flag = 0;
					break;
				default:
					throw new InternalError("Unknown class " + type);
			}
		} else flag = 8;
		flag |= Modifier.isStatic(field.getModifiers()) ? 16 : 0;
		flag |= Modifier.isVolatile(field.getModifiers()) ? 32 : 0;
		this.flag = (byte) flag;

		offset = (flag & 16) != 0 ? u.staticFieldOffset(field) : u.objectFieldOffset(field);
		if (offset == -1) {
			throw new IllegalArgumentException("Field offset error " + field);
		}
		if ((flag & 16) != 0) {
			this.inst = u.staticFieldBase(field);
		}
	}

	final void checkType(byte required) {
		if ((flag & 15) != required) throw new IllegalArgumentException(arr[flag & 15] + " cannot cast to " + arr[required]);
	}

	private final long offset;
	private Object inst;

	private Object checkAccess(Object inst) {
		if ((flag&16) != 0) {
			if (inst != null)
				throw new IllegalArgumentException("instance!=null on static field " + field);
			return this.inst;
		}
		if (!field.getDeclaringClass().isInstance(inst))
			throw new IllegalArgumentException(inst + " is not instance of " + field.getDeclaringClass().getName());
		return inst;
	}

	public Object getObject(Object inst) {
		checkType((byte) 8);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getObjectVolatile(inst, offset) : u.getObject(inst, offset);
	}

	public boolean getBoolean(Object inst) {
		checkType((byte) 0);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getBooleanVolatile(inst, offset) : u.getBoolean(inst, offset);
	}

	public byte getByte(Object inst) {
		checkType((byte) 1);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getByteVolatile(inst, offset) : u.getByte(inst, offset);
	}

	public char getChar(Object inst) {
		checkType((byte) 2);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getCharVolatile(inst, offset) : u.getChar(inst, offset);
	}

	public short getShort(Object inst) {
		checkType((byte) 3);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getShortVolatile(inst, offset) : u.getShort(inst, offset);
	}

	public int getInt(Object inst) {
		checkType((byte) 4);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getIntVolatile(inst, offset) : u.getInt(inst, offset);
	}

	public long getLong(Object inst) {
		checkType((byte) 5);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getLongVolatile(inst, offset) : u.getLong(inst, offset);
	}

	public float getFloat(Object inst) {
		checkType((byte) 6);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getFloatVolatile(inst, offset) : u.getFloat(inst, offset);
	}

	public double getDouble(Object inst) {
		checkType((byte) 7);
		inst = checkAccess(inst);
		return (flag & 32) != 0 ? u.getDoubleVolatile(inst, offset) : u.getDouble(inst, offset);
	}

	public void setObject(Object inst, Object obj) {
		if ((flag & 15) != 8) {
			switch (flag & 15) {
				case 0:
					setBoolean(inst, (Boolean) obj);
					break;
				case 1:
					setByte(inst, (Byte) obj);
					break;
				case 2:
					setChar(inst, (Character) obj);
					break;
				case 3:
					setShort(inst, (Short) obj);
					break;
				case 4:
					setInt(inst, (Integer) obj);
					break;
				case 5:
					setLong(inst, (Long) obj);
					break;
				case 6:
					setFloat(inst, (Float) obj);
					break;
				case 7:
					setDouble(inst, (Double) obj);
					break;
			}
			// unexpected
		}

		inst = checkAccess(inst);
		if (obj != null && !field.getType().isInstance(obj))
			throw new ClassCastException(obj.getClass().getName() + " cannot cast to " + field.getType().getName());
		if ((flag & 32) != 0) {
			u.putObjectVolatile(inst, offset, obj);
		} else {
			u.putObject(inst, offset, obj);
		}
	}

	public void setBoolean(Object inst, boolean value) {
		checkType((byte) 0);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putBooleanVolatile(inst, offset, value);
		} else {
			u.putBoolean(inst, offset, value);
		}
	}

	public void setByte(Object inst, byte value) {
		checkType((byte) 1);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putByteVolatile(inst, offset, value);
		} else {
			u.putByte(inst, offset, value);
		}
	}

	public void setChar(Object inst, char value) {
		checkType((byte) 2);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putCharVolatile(inst, offset, value);
		} else {
			u.putChar(inst, offset, value);
		}
	}

	public void setShort(Object inst, short value) {
		checkType((byte) 3);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putShortVolatile(inst, offset, value);
		} else {
			u.putShort(inst, offset, value);
		}
	}

	public void setInt(Object inst, int value) {
		checkType((byte) 4);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putIntVolatile(inst, offset, value);
		} else {
			u.putInt(inst, offset, value);
		}
	}

	public void setLong(Object inst, long value) {
		checkType((byte) 5);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putLongVolatile(inst, offset, value);
		} else {
			u.putLong(inst, offset, value);
		}
	}

	public void setFloat(Object inst, float value) {
		checkType((byte) 6);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putFloatVolatile(inst, offset, value);
		} else {
			u.putFloat(inst, offset, value);
		}
	}

	public void setDouble(Object inst, double value) {
		checkType((byte) 7);
		inst = checkAccess(inst);
		if ((flag & 32) != 0) {
			u.putDoubleVolatile(inst, offset, value);
		} else {
			u.putDouble(inst, offset, value);
		}
	}
}
