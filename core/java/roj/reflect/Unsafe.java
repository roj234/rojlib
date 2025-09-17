package roj.reflect;

import org.jetbrains.annotations.ApiStatus;
import roj.asm.Opcodes;
import roj.io.IOUtil;
import roj.util.Helpers;
import roj.util.JVM;

import java.lang.reflect.Field;

import static roj.reflect.Reflection.u;

/**
 * jdk.internal.misc.Unsafe的代理
 * @author Roj234
 * @since 2024/8/4 13:38
 */
public interface Unsafe {
	Unsafe U = init();
	// should be a simple static{} !
	// Lava supports.
	private static Unsafe init() {
		try {
			// hard-coded offset & size
			byte[] ref = Reflection.readExact("roj/reflect/Unsafe$.class", 6298);
			if (JVM.BIG_ENDIAN) {
				for (int i = 0xA6; i < 0xAD + 10 * 12; i += 10) {
					ref[i] = (byte) (ref[i] == 'B' ? 'L' : 'B');
				}
			}
			if (JVM.VERSION > 21) {
				ref[0x6f] = 14; // super();
				ref[0xbb8] = 14; // extends Object
				ref[0xbb8+4] = 12; // implements Runnable

				// Not able to use ACCESS_VM_ANNOTATIONS here...
				Reflection.IMPL_LOOKUP.defineClass(ref);
				ref = IOUtil.getResourceIL("roj/reflect/Unsafe$2.class");
			}

			var type = Reflection.defineClass(Unsafe.class.getClassLoader(), null, ref, 0, ref.length, Unsafe.class.getProtectionDomain(), Reflection.HIDDEN_CLASS|Reflection.ACCESS_VM_ANNOTATIONS);
			return (Unsafe) u.allocateInstance(type);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		System.err.println("[RojLib] UnsafeProxy初始化失败");
		return new Unsafe() {};
	}

	//region 偏移量 保留仅用于未来的copyMemory调用 see UTF16n
	int ARRAY_BOOLEAN_BASE_OFFSET = U.arrayBaseOffset(boolean[].class);
	int ARRAY_BYTE_BASE_OFFSET = U.arrayBaseOffset(byte[].class);
	int ARRAY_SHORT_BASE_OFFSET = U.arrayBaseOffset(short[].class);
	int ARRAY_CHAR_BASE_OFFSET = U.arrayBaseOffset(char[].class);
	int ARRAY_INT_BASE_OFFSET = U.arrayBaseOffset(int[].class);
	int ARRAY_LONG_BASE_OFFSET = U.arrayBaseOffset(long[].class);
	int ARRAY_FLOAT_BASE_OFFSET = U.arrayBaseOffset(float[].class);
	int ARRAY_DOUBLE_BASE_OFFSET = U.arrayBaseOffset(double[].class);
	int ARRAY_OBJECT_BASE_OFFSET = U.arrayBaseOffset(Object[].class);
	int ARRAY_OBJECT_INDEX_SCALE = U.arrayIndexScale(Object[].class);

	/** The value of {@code addressSize()} */
	int ADDRESS_SIZE = U.addressSize();

	default int addressSize() {return u.addressSize();}

	default long objectFieldOffset(Field f) {return u.objectFieldOffset(f);}
	default long staticFieldOffset(Field f) {return u.staticFieldOffset(f);}
	default Object staticFieldBase(Field f) {return u.staticFieldBase(f);}

	static long fieldOffset(Class<?> type, String fieldName) {
		try {
			var field = Reflection.getField(type, fieldName);
			return (field.getModifiers() & Opcodes.ACC_STATIC) == 0 ? U.objectFieldOffset(field) : U.staticFieldOffset(field);
		} catch (Exception e) {
			Helpers.athrow(e);
			return 0;
		}
	}

	/**
	 * Reports the offset of the first element in the storage allocation of a
	 * given array class.  If {@link #arrayIndexScale} returns a non-zero value
	 * for the same class, you may use that scale factor, together with this
	 * base offset, to form new offsets to access elements of arrays of the
	 * given class.
	 *
	 * @see #getInt(Object, long)
	 * @see #putInt(Object, long, int)
	 */
	default int arrayBaseOffset(Class<?> arrayClass) {return u.arrayBaseOffset(arrayClass);}

	/**
	 * Reports the scale factor for addressing elements in the storage
	 * allocation of a given array class.  However, arrays of "narrow" types
	 * will generally not work properly with accessors like {@link
	 * #getByte(Object, long)}, so the scale factor for such classes is reported
	 * as zero.
	 *
	 * @see #arrayBaseOffset
	 * @see #getInt(Object, long)
	 * @see #putInt(Object, long, int)
	 */
	default int arrayIndexScale(Class<?> arrayClass) {return u.arrayIndexScale(arrayClass);}
	//endregion
	/**
	 * 返回未初始化的基本类型数组
	 */
	default Object allocateUninitializedArray(Class<?> componentType, int length) {
		if (componentType == byte.class)    return new byte[length];
		if (componentType == boolean.class) return new boolean[length];
		if (componentType == short.class)   return new short[length];
		if (componentType == char.class)    return new char[length];
		if (componentType == int.class)     return new int[length];
		if (componentType == float.class)   return new float[length];
		if (componentType == long.class)    return new long[length];
		if (componentType == double.class)  return new double[length];
		throw new AssertionError(componentType+"不是基本类型");
	}

	/**
	 * Allocates an instance but does not run any constructor.
	 * Initializes the class if it has not yet been.
	 */
	@ApiStatus.Internal default Object allocateInstance(Class<?> cls) throws InstantiationException {return u.allocateInstance(cls);}

	/**
	 * Ensures the given class has been initialized. This is often
	 * needed in conjunction with obtaining the static field base of a
	 * class.
	 */
	@ApiStatus.Internal default void ensureClassInitialized(Class<?> c) {u.ensureClassInitialized(c);}

	//region 单地址模式 deprecated
	default byte getByte(long address) {return u.getByte(address);}
	default char getChar(long address) {return u.getChar(address);}
	default int getInt(long address) {return u.getInt(address);}
	default long getLong(long address) {return u.getLong(address);}
	default float getFloat(long address) {return u.getFloat(address);}
	default double getDouble(long address) {return u.getDouble(address);}
	default long getAddress(long address) {return u.getAddress(address);}

	default void putByte(long address, byte x) {u.putByte(address, x);}
	default void putShort(long address, short x) {u.putShort(address, x);}
	default void putChar(long address, char x) {u.putChar(address, x);}
	default void putInt(long address, int x) {u.putInt(address, x);}
	default void putLong(long address, long x) {u.putLong(address, x);}
	default void putFloat(long address, float x) {u.putFloat(address, x);}
	default void putDouble(long address, double x) {u.putDouble(address, x);}
	default void putAddress(long address, long x) {u.putAddress(address, x);}
	//endregion
	//region 内存读写 plain
	default boolean getBoolean(Object o, long offset) {return u.getBoolean(o, offset);}
	default byte getByte(Object o, long offset) {return u.getByte(o, offset);}
	default short getShort(Object o, long offset) {return u.getShort(o, offset);}
	default char getChar(Object o, long offset) {return u.getChar(o, offset);}
	default int getInt(Object o, long offset) {return u.getInt(o, offset);}
	default long getLong(Object o, long offset) {return u.getLong(o, offset);}
	default float getFloat(Object o, long offset) {return u.getFloat(o, offset);}
	default double getDouble(Object o, long offset) {return u.getDouble(o, offset);}
	default Object getObject(Object o, long offset) {return getReference(o, offset);}
	default Object getReference(Object o, long offset) {return u.getObject(o, offset);}

	default int get16UL(Object o, long offset) {return (u.getByte(o, offset++) & 0xFF) | (u.getByte(o, offset) & 0xFF) << 8;}
	default int get32UL(Object o, long offset) {return (u.getByte(o, offset++) & 0xFF) | (u.getByte(o, offset++) & 0xFF) << 8 | (u.getByte(o, offset++) & 0xFF) << 16 | (u.getByte(o, offset) & 0xFF) << 24;}
	default long get64UL(Object o, long offset) {
		return (u.getByte(o, offset++) & 0xFFL) |
			(u.getByte(o, offset++) & 0xFFL) << 8 |
			(u.getByte(o, offset++) & 0xFFL) << 16 |
			(u.getByte(o, offset++) & 0xFFL) << 24 |
			(u.getByte(o, offset++) & 0xFFL) << 32 |
			(u.getByte(o, offset++) & 0xFFL) << 40 |
			(u.getByte(o, offset++) & 0xFFL) << 48 |
			(u.getByte(o, offset) & 0xFFL) << 56;
	}

	default int get16UB(Object o, long offset) {return ((u.getByte(o, offset++) & 0xFF) << 8) | (u.getByte(o, offset) & 0xFF);}
	default int get32UB(Object o, long offset) {return (u.getByte(o, offset++) & 0xFF) << 24 | (u.getByte(o, offset++) & 0xFF) << 16 | (u.getByte(o, offset++) & 0xFF) << 8 | (u.getByte(o, offset) & 0xFF);}
	default long get64UB(Object o, long offset) {
		return (u.getByte(o, offset++) & 0xFFL) << 56 |
			(u.getByte(o, offset++) & 0xFFL) << 48 |
			(u.getByte(o, offset++) & 0xFFL) << 40 |
			(u.getByte(o, offset++) & 0xFFL) << 32 |
			(u.getByte(o, offset++) & 0xFFL) << 24 |
			(u.getByte(o, offset++) & 0xFFL) << 16 |
			(u.getByte(o, offset++) & 0xFFL) << 8 |
			u.getByte(o, offset) & 0xFFL;
	}

	default void put16UL(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) x);
		u.putByte(o, offset, (byte) (x >>> 8));
	}
	default void put24UL(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) x);
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset, (byte) (x >>> 16));
	}
	default void put32UL(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) x);
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset++, (byte) (x >>> 16));
		u.putByte(o, offset, (byte) (x >>> 24));
	}
	default void put64UL(Object o, long offset, long x) {
		u.putByte(o, offset++, (byte) x);
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset++, (byte) (x >>> 16));
		u.putByte(o, offset++, (byte) (x >>> 24));
		u.putByte(o, offset++, (byte) (x >>> 32));
		u.putByte(o, offset++, (byte) (x >>> 40));
		u.putByte(o, offset++, (byte) (x >>> 48));
		u.putByte(o, offset, (byte) (x >>> 56));
	}

	default void put16UB(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset, (byte) x);
	}
	default void put24UB(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) (x >>> 16));
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset, (byte) x);
	}
	default void put32UB(Object o, long offset, int x) {
		u.putByte(o, offset++, (byte) (x >>> 24));
		u.putByte(o, offset++, (byte) (x >>> 16));
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset, (byte) x);
	}
	default void put64UB(Object o, long offset, long x) {
		u.putByte(o, offset++, (byte) (x >>> 56));
		u.putByte(o, offset++, (byte) (x >>> 48));
		u.putByte(o, offset++, (byte) (x >>> 40));
		u.putByte(o, offset++, (byte) (x >>> 32));
		u.putByte(o, offset++, (byte) (x >>> 24));
		u.putByte(o, offset++, (byte) (x >>> 16));
		u.putByte(o, offset++, (byte) (x >>> 8));
		u.putByte(o, offset, (byte) x);
	}

	default void putBoolean(Object o, long offset, boolean x) {u.putBoolean(o, offset, x);}
	default void putByte(Object o, long offset, byte x) {u.putByte(o, offset, x);}
	default void putShort(Object o, long offset, short x) {u.putShort(o, offset, x);}
	default void putChar(Object o, long offset, char x) {u.putChar(o, offset, x);}
	default void putInt(Object o, long offset, int x) {u.putInt(o, offset, x);}
	default void putLong(Object o, long offset, long x) {u.putLong(o, offset, x);}
	default void putFloat(Object o, long offset, float x) {u.putFloat(o, offset, x);}
	default void putDouble(Object o, long offset, double x) {u.putDouble(o, offset, x);}
	default void putObject(Object o, long offset, Object x) {putReference(o, offset, x);}
	default void putReference(Object o, long offset, Object x) {u.putObject(o, offset, x);}
	//endregion
	//region 这些函数被关键（pre-transform）API使用
	@Deprecated default int getIntVolatile(Object o, long offset) {return u.getIntVolatile(o, offset);}
	@Deprecated default Object getReferenceVolatile(Object o, long offset) {return u.getObjectVolatile(o, offset);}
	@Deprecated default void putReferenceVolatile(Object o, long offset, Object x) {u.putObjectVolatile(o, offset, x);}
	@Deprecated default boolean compareAndSetInt(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	@Deprecated default boolean compareAndSetReference(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	@Deprecated default Object getAndSetReference(Object o, long offset, Object newValue) {return u.getAndSetObject(o, offset, newValue);}
	//region 内存分配
	default long allocateMemory(long bytes) {return u.allocateMemory(bytes);}
	default long reallocateMemory(long address, long bytes) {return u.reallocateMemory(address, bytes);}
	default void setMemory(Object o, long offset, long bytes, byte value) {u.setMemory(o, offset, bytes, value);}
	default void setMemory(long address, long bytes, byte value) {u.setMemory(address, bytes, value);}
	default void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {u.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);}
	default void copyMemory(long srcAddress, long destAddress, long bytes) {u.copyMemory(srcAddress, destAddress, bytes);}
	default void freeMemory(long address) {u.freeMemory(address);}
	//endregion

	/**
	 * Gets the load average in the system run queue assigned
	 * to the available processors averaged over various periods of time.
	 * This method retrieves the given {@code nelem} samples and
	 * assigns to the elements of the given {@code loadavg} array.
	 * The system imposes a maximum of 3 samples, representing
	 * averages over the last 1,  5,  and  15 minutes, respectively.
	 *
	 * @param loadavg an array of double of size nelems
	 * @param nelems the number of samples to be retrieved and
	 *        must be 1 to 3.
	 *
	 * @return the number of samples actually retrieved; or -1
	 *         if the load average is unobtainable.
	 */
	default int getLoadAverage(double[] loadavg, int nelems) {return u.getLoadAverage(loadavg, nelems);}
}