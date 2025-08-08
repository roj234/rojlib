package roj.reflect;

import org.jetbrains.annotations.ApiStatus;
import roj.asm.Opcodes;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.lang.reflect.Field;

import static roj.reflect.Reflection.u;

/**
 * jdk.internal.misc.Unsafe的代理
 * @author Roj234
 * @since 2024/8/4 13:38
 */
public interface Unaligned {
	Unaligned U = init();
	// should be a simple static{} !
	// Lava supports.
	private static Unaligned init() {
		try {
			// hard-coded offset & size
			byte[] ref = Reflection.readExact("roj/reflect/Unaligned$.class", 14140);
			if (Reflection.BIG_ENDIAN) {
				for (int i = 0xAD; i < 0xAD + 10 * 12; i += 10) {
					ref[i] = (byte) (ref[i] == 'B' ? 'L' : 'B');
				}
			}
			if (Reflection.JAVA_VERSION > 21) {
				ref[0x73] = 14; // super();
				ref[0x17DA] = 14; // extends Object
				ref[0x17DE] = 12; // implements Runnable

				// Not able to use ACCESS_VM_ANNOTATIONS here...
				Reflection.IMPL_LOOKUP.defineClass(ref);
				ref = IOUtil.getResourceIL("roj/reflect/Unaligned$2.class");
			}
			var type = Reflection.DefineWeakClass("roj.reflect.Unaligned", ref);
			return (Unaligned) u.allocateInstance(type);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		System.err.println("[RojLib] Unaligned初始化失败");
		return new Unaligned() {};
	}
	//region 偏移量
	/**
	 * This constant differs from all results that will ever be returned from
	 * {@link #staticFieldOffset}, {@link #objectFieldOffset},
	 * or {@link #arrayBaseOffset}.
	 */
	int INVALID_FIELD_OFFSET = -1;
	int ARRAY_BOOLEAN_BASE_OFFSET = U.arrayBaseOffset(boolean[].class);
	int ARRAY_BYTE_BASE_OFFSET = U.arrayBaseOffset(byte[].class);
	int ARRAY_SHORT_BASE_OFFSET = U.arrayBaseOffset(short[].class);
	int ARRAY_CHAR_BASE_OFFSET = U.arrayBaseOffset(char[].class);
	int ARRAY_INT_BASE_OFFSET = U.arrayBaseOffset(int[].class);
	int ARRAY_LONG_BASE_OFFSET = U.arrayBaseOffset(long[].class);
	int ARRAY_FLOAT_BASE_OFFSET = U.arrayBaseOffset(float[].class);
	int ARRAY_DOUBLE_BASE_OFFSET = U.arrayBaseOffset(double[].class);
	int ARRAY_OBJECT_BASE_OFFSET = U.arrayBaseOffset(Object[].class);
	int ARRAY_BOOLEAN_INDEX_SCALE = 1;
	int ARRAY_BYTE_INDEX_SCALE = 1;
	int ARRAY_SHORT_INDEX_SCALE = 2;
	int ARRAY_CHAR_INDEX_SCALE = 2;
	int ARRAY_INT_INDEX_SCALE = 4;
	int ARRAY_LONG_INDEX_SCALE = 8;
	int ARRAY_FLOAT_INDEX_SCALE = 4;
	int ARRAY_DOUBLE_INDEX_SCALE = 8;
	int ARRAY_OBJECT_INDEX_SCALE = U.arrayIndexScale(Object[].class);

	/** The value of {@code addressSize()} */
	int ADDRESS_SIZE = U.addressSize();
	/**
	 * Reports the size in bytes of a native memory page (whatever that is).
	 * This value will always be a power of two.
	 */
	int PAGE_SIZE = U.pageSize();

	@ApiStatus.Internal default int addressSize() {return u.addressSize();}
	@ApiStatus.Internal default int pageSize() {return u.pageSize();}

	/**
	 * Use {@link Unaligned#fieldOffset(Class, String)} instead
	 */
	@ApiStatus.Internal default long objectFieldOffset(Field f) {return u.objectFieldOffset(f);}
	/**
	 * Use {@link Unaligned#fieldOffset(Class, String)} instead
	 */
	@ApiStatus.Internal default long staticFieldOffset(Field f) {return u.staticFieldOffset(f);}
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
	@ApiStatus.Internal default Object staticFieldBase(Field f) {return u.staticFieldBase(f);}

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
	 * Detects if the given class may need to be initialized. This is often
	 * needed in conjunction with obtaining the static field base of a
	 * class.
	 *
	 * @return false only if a call to {@code ensureClassInitialized} would have no effect
	 *
	 */
	@ApiStatus.Internal default boolean shouldBeInitialized(Class<?> c) {return u.shouldBeInitialized(c);}

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
	@Name("")
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

	default void putBoolean(Object o, long offset, boolean x) {u.putBoolean(o, offset, x);}
	default void putByte(Object o, long offset, byte x) {u.putByte(o, offset, x);}
	default void putShort(Object o, long offset, short x) {u.putShort(o, offset, x);}
	default void putChar(Object o, long offset, char x) {u.putChar(o, offset, x);}
	default void putInt(Object o, long offset, int x) {u.putInt(o, offset, x);}
	default void putLong(Object o, long offset, long x) {u.putLong(o, offset, x);}
	default void putFloat(Object o, long offset, float x) {u.putFloat(o, offset, x);}
	default void putDouble(Object o, long offset, double x) {u.putDouble(o, offset, x);}
	@Name("")
	default void putObject(Object o, long offset, Object x) {putReference(o, offset, x);}
	default void putReference(Object o, long offset, Object x) {u.putObject(o, offset, x);}
	//endregion
	//region 内存读写 volatile acquire release opaque
	default boolean getBooleanVolatile(Object o, long offset) {return u.getBooleanVolatile(o, offset);}
	default byte getByteVolatile(Object o, long offset) {return u.getByteVolatile(o, offset);}
	default short getShortVolatile(Object o, long offset) {return u.getShortVolatile(o, offset);}
	default char getCharVolatile(Object o, long offset) {return u.getCharVolatile(o, offset);}
	default int getIntVolatile(Object o, long offset) {return u.getIntVolatile(o, offset);}
	default long getLongVolatile(Object o, long offset) {return u.getLongVolatile(o, offset);}
	default float getFloatVolatile(Object o, long offset) {return u.getFloatVolatile(o, offset);}
	default double getDoubleVolatile(Object o, long offset) {return u.getDoubleVolatile(o, offset);}
	/**
	 * Fetches a reference value from a given Java variable, with volatile
	 * load semantics. Otherwise identical to {@link #getReference(Object, long)}
	 */
	default Object getReferenceVolatile(Object o, long offset) {return u.getObjectVolatile(o, offset);}

	default void putBooleanVolatile(Object o, long offset, boolean x) {u.putBooleanVolatile(o, offset, x);}
	default void putByteVolatile(Object o, long offset, byte x) {u.putByteVolatile(o, offset, x);}
	default void putShortVolatile(Object o, long offset, short x) {u.putShortVolatile(o, offset, x);}
	default void putCharVolatile(Object o, long offset, char x) {u.putCharVolatile(o, offset, x);}
	default void putIntVolatile(Object o, long offset, int x) {u.putIntVolatile(o, offset, x);}
	default void putLongVolatile(Object o, long offset, long x) {u.putLongVolatile(o, offset, x);}
	default void putFloatVolatile(Object o, long offset, float x) {u.putFloatVolatile(o, offset, x);}
	default void putDoubleVolatile(Object o, long offset, double x) {u.putDoubleVolatile(o, offset, x);}
	default void putReferenceVolatile(Object o, long offset, Object x) {u.putObjectVolatile(o, offset, x);}

	/** @see #getReferenceAcquire(Object, long) */
	default boolean getBooleanAcquire(Object o, long offset) {return u.getBooleanVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default byte getByteAcquire(Object o, long offset) {return u.getByteVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default short getShortAcquire(Object o, long offset) {return u.getShortVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default char getCharAcquire(Object o, long offset) {return u.getCharVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default int getIntAcquire(Object o, long offset) {return u.getIntVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default long getLongAcquire(Object o, long offset) {return u.getLongVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default float getFloatAcquire(Object o, long offset) {return u.getFloatVolatile(o, offset);}
	/** @see #getReferenceAcquire(Object, long) */
	default double getDoubleAcquire(Object o, long offset) {return u.getDoubleVolatile(o, offset);}
	/**
	 * Acquire version of {@link #getReferenceVolatile(Object, long)}
	 */
	default Object getReferenceAcquire(Object o, long offset) {return u.getObjectVolatile(o, offset);}

	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putBooleanAcquire(Object o, long offset, boolean x) {u.putBooleanVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putByteAcquire(Object o, long offset, byte x) {u.putByteVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putShortAcquire(Object o, long offset, short x) {u.putShortVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putCharAcquire(Object o, long offset, char x) {u.putCharVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putIntAcquire(Object o, long offset, int x) {u.putIntVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putLongAcquire(Object o, long offset, long x) {u.putLongVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putFloatAcquire(Object o, long offset, float x) {u.putFloatVolatile(o, offset, x);}
	/** @see #putReferenceAcquire(Object, long, Object) */
	default void putDoubleAcquire(Object o, long offset, double x) {u.putDoubleVolatile(o, offset, x);}
	/**
	 * Acquire version of {@link #putReferenceVolatile(Object, long, Object)}
	 */
	default void putReferenceAcquire(Object o, long offset, Object x) {u.putObjectVolatile(o, offset, x);}

	/** @see #putReferenceRelease(Object, long, Object) */
	default void putByteRelease(Object o, long offset, byte x) {u.putByteVolatile(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putShortRelease(Object o, long offset, short x) {u.putShortVolatile(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putCharRelease(Object o, long offset, char x) {u.putCharVolatile(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putIntRelease(Object o, long offset, int x) {u.putOrderedInt(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putLongRelease(Object o, long offset, long x) {u.putOrderedLong(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putFloatRelease(Object o, long offset, float x) {u.putFloatVolatile(o, offset, x);}
	/** @see #putReferenceRelease(Object, long, Object) */
	default void putDoubleRelease(Object o, long offset, double x) {u.putDoubleVolatile(o, offset, x);}
	/**
	 * Version of {@link #putReferenceVolatile(Object, long, Object)}
	 * that does not guarantee immediate visibility of the store to
	 * other threads. This method is generally only useful if the
	 * underlying field is a Java volatile (or if an array cell, one
	 * that is otherwise only accessed using volatile accesses).
	 * <p>
	 * Corresponds to C11 atomic_store_explicit(..., memory_order_release).
	 */
	default void putReferenceRelease(Object o, long offset, Object x) {u.putOrderedObject(o, offset, x);}

	/** @see #getReferenceOpaque(Object, long) */
	default boolean getBooleanOpaque(Object o, long offset) {return u.getBooleanVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default byte getByteOpaque(Object o, long offset) {return u.getByteVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default short getShortOpaque(Object o, long offset) {return u.getShortVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default char getCharOpaque(Object o, long offset) {return u.getCharVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default int getIntOpaque(Object o, long offset) {return u.getIntVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default long getLongOpaque(Object o, long offset) {return u.getLongVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default float getFloatOpaque(Object o, long offset) {return u.getFloatVolatile(o, offset);}
	/** @see #getReferenceOpaque(Object, long) */
	default double getDoubleOpaque(Object o, long offset) {return u.getDoubleVolatile(o, offset);}
	/**
	 * Opaque version of {@link #getReferenceVolatile(Object, long)}
	 */
	default Object getReferenceOpaque(Object o, long offset) {return u.getObjectVolatile(o, offset);}

	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putBooleanOpaque(Object o, long offset, boolean x) {u.putBooleanVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putByteOpaque(Object o, long offset, byte x) {u.putByteVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putShortOpaque(Object o, long offset, short x) {u.putShortVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putCharOpaque(Object o, long offset, char x) {u.putCharVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putIntOpaque(Object o, long offset, int x) {u.putIntVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putLongOpaque(Object o, long offset, long x) {u.putLongVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putFloatOpaque(Object o, long offset, float x) {u.putFloatVolatile(o, offset, x);}
	/** @see #putReferenceOpaque(Object, long, Object) */
	default void putDoubleOpaque(Object o, long offset, double x) {u.putDoubleVolatile(o, offset, x);}
	/**
	 * Opaque version of {@link #putReferenceVolatile(Object, long, Object)}
	 */
	default void putReferenceOpaque(Object o, long offset, Object x) {u.putObjectVolatile(o, offset, x);}
	//endregion
	//region CAS, volatile acquire release opaque
	/** @see #compareAndSetReference(Object, long, Object, Object) */
	default boolean compareAndSetInt(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean compareAndSetIntAcquire(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean compareAndSetIntRelease(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean compareAndSetIntPlain(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}

	/** @see #compareAndSetReference(Object, long, Object, Object) */
	default boolean compareAndSetLong(Object o, long offset, long expected, long x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean compareAndSetLongAcquire(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean compareAndSetLongRelease(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean compareAndSetLongPlain(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	/**
	 * Atomically updates Java variable to {@code x} if it is currently
	 * holding {@code expected}.
	 *
	 * <p>This operation has memory semantics of a {@code volatile} read
	 * and write.  Corresponds to C11 atomic_compare_exchange_strong.
	 *
	 * @return {@code true} if successful
	 */
	default boolean compareAndSetReference(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean compareAndSetReferenceAcquire(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean compareAndSetReferenceRelease(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean compareAndSetReferencePlain(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}

	/** @see #weakCompareAndSetReference(Object, long, Object, Object) */
	default boolean weakCompareAndSetInt(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean weakCompareAndSetIntAcquire(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean weakCompareAndSetIntRelease(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}
	default boolean weakCompareAndSetIntPlain(Object o, long offset, int expected, int x) {return u.compareAndSwapInt(o, offset, expected, x);}

	/** @see #weakCompareAndSetReference(Object, long, Object, Object) */
	default boolean weakCompareAndSetLong(Object o, long offset, long expected, long x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean weakCompareAndSetLongAcquire(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean weakCompareAndSetLongRelease(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	default boolean weakCompareAndSetLongPlain(Object o, long offset, int expected, int x) {return u.compareAndSwapLong(o, offset, expected, x);}
	/**
	 * Atomically updates Java variable to {@code x} if it is currently
	 * holding {@code expected}.
	 *
	 * <p>This operation has memory semantics of a {@code volatile} read
	 * and write.  Corresponds to C11 atomic_compare_exchange_strong.
	 *
	 * @return {@code true} if successful
	 */
	default boolean weakCompareAndSetReference(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean weakCompareAndSetReferenceAcquire(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean weakCompareAndSetReferenceRelease(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}
	default boolean weakCompareAndSetReferencePlain(Object o, long offset, Object expected, Object x) {return u.compareAndSwapObject(o, offset, expected, x);}


	default int getAndAddInt(Object o, long offset, int delta) {return u.getAndAddInt(o, offset, delta);}
	default int getAndAddIntRelease(Object o, long offset, int delta) {return u.getAndAddInt(o, offset, delta);}
	default int getAndAddIntAcquire(Object o, long offset, int delta) {return u.getAndAddInt(o, offset, delta);}

	default long getAndAddLong(Object o, long offset, long delta) {return u.getAndAddLong(o, offset, delta);}
	default long getAndAddLongRelease(Object o, long offset, long delta) {return u.getAndAddLong(o, offset, delta);}
	default long getAndAddLongAcquire(Object o, long offset, long delta) {return u.getAndAddLong(o, offset, delta);}

	default int getAndSetInt(Object o, long offset, int newValue) {return u.getAndSetInt(o, offset, newValue);}
	default int getAndSetIntRelease(Object o, long offset, int newValue) {return u.getAndSetInt(o, offset, newValue);}
	default int getAndSetIntAcquire(Object o, long offset, int newValue) {return u.getAndSetInt(o, offset, newValue);}

	default long getAndSetLong(Object o, long offset, long newValue) {return u.getAndSetLong(o, offset, newValue);}
	default long getAndSetLongRelease(Object o, long offset, long newValue) {return u.getAndSetLong(o, offset, newValue);}
	default long getAndSetLongAcquire(Object o, long offset, long newValue) {return u.getAndSetLong(o, offset, newValue);}

	default Object getAndSetReference(Object o, long offset, Object newValue) {return u.getAndSetObject(o, offset, newValue);}
	default Object getAndSetReferenceRelease(Object o, long offset, Object newValue) {return u.getAndSetObject(o, offset, newValue);}
	default Object getAndSetReferenceAcquire(Object o, long offset, Object newValue) {return u.getAndSetObject(o, offset, newValue);}
	//endregion
	//region 内存屏障
	@Deprecated
	@Name("")
	default void storeFence() {Handles.storeFence();}
	//endregion
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