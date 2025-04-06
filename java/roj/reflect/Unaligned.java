package roj.reflect;

import roj.io.IOUtil;
import roj.text.logging.Logger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static roj.reflect.VMInternals.u;

/**
 * 允许运行时不存在sun.misc.Unsafe的情况(WIP)
 * @author Roj234
 * @since 2024/8/4 0004 13:38
 */
public interface Unaligned {
	//region fields initialize first
	/**
	 * This constant differs from all results that will ever be returned from
	 * {@link #staticFieldOffset}, {@link #objectFieldOffset},
	 * or {@link #arrayBaseOffset}.
	 */
	int INVALID_FIELD_OFFSET = -1;

	/**
	 * The value of {@code arrayBaseOffset(boolean[].class)}
	 */
	int ARRAY_BOOLEAN_BASE_OFFSET = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(byte[].class)}
	 */
	int ARRAY_BYTE_BASE_OFFSET = Unsafe.ARRAY_BYTE_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(short[].class)}
	 */
	int ARRAY_SHORT_BASE_OFFSET = Unsafe.ARRAY_SHORT_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(char[].class)}
	 */
	int ARRAY_CHAR_BASE_OFFSET = Unsafe.ARRAY_CHAR_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(int[].class)}
	 */
	int ARRAY_INT_BASE_OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(long[].class)}
	 */
	int ARRAY_LONG_BASE_OFFSET = Unsafe.ARRAY_LONG_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(float[].class)}
	 */
	int ARRAY_FLOAT_BASE_OFFSET = Unsafe.ARRAY_FLOAT_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(double[].class)}
	 */
	int ARRAY_DOUBLE_BASE_OFFSET = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;

	/**
	 * The value of {@code arrayBaseOffset(Object[].class)}
	 */
	int ARRAY_OBJECT_BASE_OFFSET = Unsafe.ARRAY_OBJECT_BASE_OFFSET;

	int ARRAY_BOOLEAN_INDEX_SCALE = 1;
	int ARRAY_BYTE_INDEX_SCALE = 1;
	int ARRAY_SHORT_INDEX_SCALE = 2;
	int ARRAY_CHAR_INDEX_SCALE = 2;
	int ARRAY_INT_INDEX_SCALE = 4;
	int ARRAY_LONG_INDEX_SCALE = 8;
	int ARRAY_FLOAT_INDEX_SCALE = 4;
	int ARRAY_DOuBLE_INDEX_SCALE = 8;
	/**
	 * The value of {@code arrayIndexScale(Object[].class)}
	 */
	int ARRAY_OBJECT_INDEX_SCALE = Unsafe.ARRAY_OBJECT_INDEX_SCALE;

	/** The value of {@code addressSize()} */
	int ADDRESS_SIZE = Unsafe.ADDRESS_SIZE;
	//endregion

	@interface Name { String value();}

	Unaligned U = init();
	private static Unaligned init() {
		if (ReflectionUtils.JAVA_VERSION > 8) {
			try {
				byte[] ref = IOUtil.getResource("roj/reflect/Unaligned$.class", Unaligned.class);
				if (ref.length != 8776) throw new AssertionError("data corrupt");
				if (ReflectionUtils.BIG_ENDIAN) {
					for (int i = 3804; i < 3804 + 10 * 12; i += 10) {
						ref[i] = (byte) (ref[i] == 'B' ? 'L' : 'B');
					}
				}
				if (ReflectionUtils.JAVA_VERSION > 21) {
					ref[ 115] = 14; // super();
					ref[4094] = 14; // extends Object
					ref[4098] = 12; // implements Runnable

					// Not able to use ACCESS_VM_ANNOTATIONS here...
					VMInternals._ImplLookup.defineClass(ref);
					ref = IOUtil.getResource("roj/reflect/Unaligned$2.class", Unaligned.class);
				}
				// it actually does, it should have static{} !
				// lavac does.
				var type = VMInternals.DefineWeakClass("roj.reflect.Unaligned", ref);
				return (Unaligned) u.allocateInstance(type);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		Logger.FALLBACK.fatal("NativeAccess初始化失败！！！");
		return new Unaligned() {};
	}

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
		U.putByte(o, offset++, (byte) x);
		U.putByte(o, offset++, (byte) (x >>> 8));
		U.putByte(o, offset, (byte) (x >>> 16));
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
		U.putByte(o, offset++, (byte) (x >>> 16));
		U.putByte(o, offset++, (byte) (x >>> 8));
		U.putByte(o, offset, (byte) x);
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

	default int getInt(Object o, long offset) {return u.getInt(o, offset);}
	default void putInt(Object o, long offset, int x) {u.putInt(o, offset, x);}
	default Object getObject(Object o, long offset) {return u.getObject(o, offset);}

	default void putObject(Object o, long offset, Object x) {u.putObject(o, offset, x);}
	default boolean getBoolean(Object o, long offset) {return u.getBoolean(o, offset);}
	default void putBoolean(Object o, long offset, boolean x) {u.putBoolean(o, offset, x);}
	default byte getByte(Object o, long offset) {return u.getByte(o, offset);}
	default void putByte(Object o, long offset, byte x) {u.putByte(o, offset, x);}
	default short getShort(Object o, long offset) {return u.getShort(o, offset);}
	default void putShort(Object o, long offset, short x) {u.putShort(o, offset, x);}
	default char getChar(Object o, long offset) {return u.getChar(o, offset);}
	default void putChar(Object o, long offset, char x) {u.putChar(o, offset, x);}
	default long getLong(Object o, long offset) {return u.getLong(o, offset);}
	default void putLong(Object o, long offset, long x) {u.putLong(o, offset, x);}
	default float getFloat(Object o, long offset) {return u.getFloat(o, offset);}
	default void putFloat(Object o, long offset, float x) {u.putFloat(o, offset, x);}
	default double getDouble(Object o, long offset) {return u.getDouble(o, offset);}
	default void putDouble(Object o, long offset, double x) {u.putDouble(o, offset, x);}

	default byte getByte(long address) {return u.getByte(address);}
	default void putByte(long address, byte x) {u.putByte(address, x);}

	default short getShort(long address) {return u.getShort(address);}
	default void putShort(long address, short x) {u.putShort(address, x);}

	default char getChar(long address) {return u.getChar(address);}
	default void putChar(long address, char x) {u.putChar(address, x);}

	default int getInt(long address) {return u.getInt(address);}
	default void putInt(long address, int x) {u.putInt(address, x);}

	default long getLong(long address) {return u.getLong(address);}
	default void putLong(long address, long x) {u.putLong(address, x);}

	default float getFloat(long address) {return u.getFloat(address);}
	default void putFloat(long address, float x) {u.putFloat(address, x);}

	default double getDouble(long address) {return u.getDouble(address);}
	default void putDouble(long address, double x) {u.putDouble(address, x);}

	default long getAddress(long address) {return u.getAddress(address);}
	default void putAddress(long address, long x) {u.putAddress(address, x);}

	default long allocateMemory(long bytes) {return u.allocateMemory(bytes);}
	default long reallocateMemory(long address, long bytes) {return u.reallocateMemory(address, bytes);}
	default void setMemory(Object o, long offset, long bytes, byte value) {u.setMemory(o, offset, bytes, value);}
	default void setMemory(long address, long bytes, byte value) {u.setMemory(address, bytes, value);}
	default void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {u.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes);}
	default void copyMemory(long srcAddress, long destAddress, long bytes) {u.copyMemory(srcAddress, destAddress, bytes);}
	default void freeMemory(long address) {u.freeMemory(address);}

	/// random queries

	/**
	 * Reports the location of a given field in the storage allocation of its
	 * class.  Do not expect to perform any sort of arithmetic on this offset;
	 * it is just a cookie which is passed to the unsafe heap memory accessors.
	 *
	 * <p>Any given field will always have the same offset and base, and no
	 * two distinct fields of the same class will ever have the same offset
	 * and base.
	 *
	 * <p>As of 1.4.1, offsets for fields are represented as long values,
	 * although the Sun JVM does not use the most significant 32 bits.
	 * However, JVM implementations which store static fields at absolute
	 * addresses can use long offsets and null base pointers to express
	 * the field locations in a form usable by {@link #getInt(Object,long)}.
	 * Therefore, code which will be ported to such JVMs on 64-bit platforms
	 * must preserve all bits of static field offsets.
	 * @see #getInt(Object, long)
	 */
	default long objectFieldOffset(Field f) {return u.objectFieldOffset(f);}

	/**
	 * Reports the location of a given static field, in conjunction with {@link
	 * #staticFieldBase}.
	 * <p>Do not expect to perform any sort of arithmetic on this offset;
	 * it is just a cookie which is passed to the unsafe heap memory accessors.
	 *
	 * <p>Any given field will always have the same offset, and no two distinct
	 * fields of the same class will ever have the same offset.
	 *
	 * <p>As of 1.4.1, offsets for fields are represented as long values,
	 * although the Sun JVM does not use the most significant 32 bits.
	 * It is hard to imagine a JVM technology which needs more than
	 * a few bits to encode an offset within a non-array object,
	 * However, for consistency with other methods in this class,
	 * this method reports its result as a long value.
	 * @see #getInt(Object, long)
	 */
	default long staticFieldOffset(Field f) {return u.staticFieldOffset(f);}

	/**
	 * Reports the location of a given static field, in conjunction with {@link
	 * #staticFieldOffset}.
	 * <p>Fetch the base "Object", if any, with which static fields of the
	 * given class can be accessed via methods like {@link #getInt(Object,
	 * long)}.  This value may be null.  This value may refer to an object
	 * which is a "cookie", not guaranteed to be a real Object, and it should
	 * not be used in any way except as argument to the get and put routines in
	 * this class.
	 */
	default Object staticFieldBase(Field f) {return u.staticFieldBase(f);}

	/**
	 * Detects if the given class may need to be initialized. This is often
	 * needed in conjunction with obtaining the static field base of a
	 * class.
	 *
	 * @return false only if a call to {@code ensureClassInitialized} would have no effect
	 *
	 */
	default boolean shouldBeInitialized(Class<?> c) {return u.shouldBeInitialized(c);}

	/**
	 * Ensures the given class has been initialized. This is often
	 * needed in conjunction with obtaining the static field base of a
	 * class.
	 */
	default void ensureClassInitialized(Class<?> c) {u.ensureClassInitialized(c);}

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

	/**
	 * Reports the size in bytes of a native memory page (whatever that is).
	 * This value will always be a power of two.
	 */
	default int pageSize() {return u.pageSize();}


	/// random trusted operations from JNI:

	/**
	 * Allocates an instance but does not run any constructor.
	 * Initializes the class if it has not yet been.
	 */
	default Object allocateInstance(Class<?> cls) throws InstantiationException {return u.allocateInstance(cls);}

	/**
	 * Atomically updates Java variable to {@code x} if it is currently
	 * holding {@code expected}.
	 *
	 * <p>This operation has memory semantics of a {@code volatile} read
	 * and write.  Corresponds to C11 atomic_compare_exchange_strong.
	 *
	 * @return {@code true} if successful
	 */
	@Name("compareAndSetReference")
	default boolean compareAndSwapObject(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}

	/**
	 * Atomically updates Java variable to {@code x} if it is currently
	 * holding {@code expected}.
	 *
	 * <p>This operation has memory semantics of a {@code volatile} read
	 * and write.  Corresponds to C11 atomic_compare_exchange_strong.
	 *
	 * @return {@code true} if successful
	 */
	@Name("compareAndSetInt")
	default boolean compareAndSwapInt(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}

	/**
	 * Atomically updates Java variable to {@code x} if it is currently
	 * holding {@code expected}.
	 *
	 * <p>This operation has memory semantics of a {@code volatile} read
	 * and write.  Corresponds to C11 atomic_compare_exchange_strong.
	 *
	 * @return {@code true} if successful
	 */
	@Name("compareAndSetLong")
	default boolean compareAndSwapLong(Object o, long offset, long expected, long x) {return u.compareAndSwapLong(o, offset, expected, x);}

	/**
	 * Fetches a reference value from a given Java variable, with volatile
	 * load semantics. Otherwise identical to {@link #getObject(Object, long)}
	 */
	@Name("getReferenceVolatile")
	default Object getObjectVolatile(Object o, long offset) {return u.getObjectVolatile(o, offset);}
	@Name("putReferenceVolatile")
	default void putObjectVolatile(Object o, long offset, Object x) {u.putObjectVolatile(o, offset, x);}
	default int getIntVolatile(Object o, long offset) {return u.getIntVolatile(o, offset);}
	default void putIntVolatile(Object o, long offset, int x) {u.putIntVolatile(o, offset, x);}
	default boolean getBooleanVolatile(Object o, long offset) {return u.getBooleanVolatile(o, offset);}
	default void putBooleanVolatile(Object o, long offset, boolean x) {u.putBooleanVolatile(o, offset, x);}
	default byte getByteVolatile(Object o, long offset) {return u.getByteVolatile(o, offset);}
	default void putByteVolatile(Object o, long offset, byte x) {u.putByteVolatile(o, offset, x);}
	default short getShortVolatile(Object o, long offset) {return u.getShortVolatile(o, offset);}
	default void putShortVolatile(Object o, long offset, short x) {u.putShortVolatile(o, offset, x);}
	default char getCharVolatile(Object o, long offset) {return u.getCharVolatile(o, offset);}
	default void putCharVolatile(Object o, long offset, char x) {u.putCharVolatile(o, offset, x);}
	default long getLongVolatile(Object o, long offset) {return u.getLongVolatile(o, offset);}
	default void putLongVolatile(Object o, long offset, long x) {u.putLongVolatile(o, offset, x);}
	default float getFloatVolatile(Object o, long offset) {return u.getFloatVolatile(o, offset);}
	default void putFloatVolatile(Object o, long offset, float x) {u.putFloatVolatile(o, offset, x);}
	default double getDoubleVolatile(Object o, long offset) {return u.getDoubleVolatile(o, offset);}
	default void putDoubleVolatile(Object o, long offset, double x) {u.putDoubleVolatile(o, offset, x);}

	/**
	 * Version of {@link #putObjectVolatile(Object, long, Object)}
	 * that does not guarantee immediate visibility of the store to
	 * other threads. This method is generally only useful if the
	 * underlying field is a Java volatile (or if an array cell, one
	 * that is otherwise only accessed using volatile accesses).
	 *
	 * Corresponds to C11 atomic_store_explicit(..., memory_order_release).
	 */
	@Name("putReferenceRelease")
	default void putOrderedObject(Object o, long offset, Object x) {u.putOrderedObject(o, offset, x);}

	/** Ordered/Lazy version of {@link #putIntVolatile(Object, long, int)}  */
	@Name("putIntRelease")
	default void putOrderedInt(Object o, long offset, int x) {u.putOrderedInt(o, offset, x);}

	/** Ordered/Lazy version of {@link #putLongVolatile(Object, long, long)} */
	@Name("putLongRelease")
	default void putOrderedLong(Object o, long offset, long x) {u.putOrderedLong(o, offset, x);}

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
	default int getAndAddInt(Object o, long offset, int delta) {return u.getAndAddInt(o, offset, delta);}
	default long getAndAddLong(Object o, long offset, long delta) {return u.getAndAddLong(o, offset, delta);}
	default int getAndSetInt(Object o, long offset, int newValue) {return u.getAndSetInt(o, offset, newValue);}
	default long getAndSetLong(Object o, long offset, long newValue) {return u.getAndSetLong(o, offset, newValue);}

	@Name("getAndSetReference")
	default Object getAndSetObject(Object o, long offset, Object newValue) {return u.getAndSetObject(o, offset, newValue);}

	/**
	 * Ensures that loads before the fence will not be reordered with loads and
	 * stores after the fence; a "LoadLoad plus LoadStore barrier".
	 *
	 * Corresponds to C11 atomic_thread_fence(memory_order_acquire)
	 * (an "acquire fence").
	 *
	 * Provides a LoadLoad barrier followed by a LoadStore barrier.
	 *
	 * @since 1.8
	 */
	default void loadFence() {u.loadFence();}
	/**
	 * Ensures that loads and stores before the fence will not be reordered with
	 * stores after the fence; a "StoreStore plus LoadStore barrier".
	 *
	 * Corresponds to C11 atomic_thread_fence(memory_order_release)
	 * (a "release fence").
	 *
	 * Provides a StoreStore barrier followed by a LoadStore barrier.
	 *
	 *
	 * @since 1.8
	 */
	default void storeFence() {u.storeFence();}
	/**
	 * Ensures that loads and stores before the fence will not be reordered
	 * with loads and stores after the fence.  Implies the effects of both
	 * loadFence() and storeFence(), and in addition, the effect of a StoreLoad
	 * barrier.
	 *
	 * Corresponds to C11 atomic_thread_fence(memory_order_seq_cst).
	 * @since 1.8
	 */
	default void fullFence() {u.fullFence();}

	default void invokeCleaner(java.nio.ByteBuffer directBuffer) {u.invokeCleaner(directBuffer);}
}