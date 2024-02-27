package roj.reflect;

import roj.io.IOUtil;

import java.nio.ByteOrder;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/8/4 0004 13:38
 */
public interface Unaligned {
	Unaligned U = init();
	private static Unaligned init() {
		Unaligned preImpl = new Unaligned() {};
		if (VMInternals.JAVA_VERSION > 8) {
			var nativeBE = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
			try {
				var offset = ReflectionUtils.fieldOffset(Unaligned.class, "U");
				u.putObject(Unaligned.class, offset, preImpl);

				var resource = IOUtil.getResource("roj/reflect/Unaligned$"+(nativeBE?"BE":"LE")+".class");
				// it actually does, it should have static{} !
				// lavac does.
				var type = VMInternals.DefineWeakClass("roj.reflect.Unaligned", resource);
				return (Unaligned) ClassDefiner.postMake(type);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		return preImpl;
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
}