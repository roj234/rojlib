package roj.util;

import java.lang.reflect.Array;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/1/7 0007 2:46
 */
public final class NativeArray {
	private static final boolean CHECK = true;

	public final Object ref;
	public final long addr;
	public final int compSize;
	public final int length;

	private NativeArray(Object ref, long addr, int compSize, int length) {
		this.ref = ref;
		this.addr = addr;
		this.compSize = compSize;
		this.length = length;
	}

	public static NativeArray create(long addr, int compSize, int length) { return create(null, addr, compSize, length); }
	public static NativeArray create(Object ref, long addr, int compSize, int length) { return new NativeArray(ref, addr, compSize, length); }

	public static NativeArray primitiveArray(Object b) {
		if (!b.getClass().getComponentType().isPrimitive()) throw new IllegalArgumentException(b.getClass()+" is not primitive array");
		Class<?> arrayClass = b.getClass();
		return create(b, U.arrayBaseOffset(arrayClass), U.arrayIndexScale(arrayClass), Array.getLength(b));
	}
	public static NativeArray objectArray(Object[] b) { return create(b, 0, 4, b.length); }

	public boolean isObjectArray() { return ref != null && !ref.getClass().getComponentType().isPrimitive(); }

	public void copyTo(int off, int count, Object dstRef, long dstOff, int stride) {
		if (CHECK) checkRange(off, count);

		if (isObjectArray()) {
			while (count-- > 0) {
				U.putInt(dstRef, dstOff, off++);
				dstOff += stride;
			}
		} else {
			long offset = (long) off*compSize + addr;
			while (count-- > 0) {
				U.copyMemory(ref, offset, dstRef, dstOff, compSize);
				offset += compSize;
				dstOff += stride;
			}
		}
	}
	public void copyFrom(Object srcRef, long srcOff, int off, int count, int stride) {
		if (CHECK) checkRange(off, count);

		if (isObjectArray()) {
			Object[] arr = (Object[]) ref;
			Object[] bak = arr.clone();

			while (count-- > 0) {
				arr[off++] = bak[U.getInt(srcRef, srcOff)];
				srcOff += stride;
			}
		} else {
			long offset = (long) off*compSize + addr;
			while (count-- > 0) {
				U.copyMemory(srcRef, srcOff, ref, offset, compSize);
				srcOff += stride;
				offset += compSize;
			}
		}
	}

	public void set(int pos, int val) {
		if (CHECK) checkRange(pos, 1);
		U.putByte(ref, addr+pos, (byte) val);
	}
	public byte get(int pos) {
		if (CHECK) checkRange(pos, 1);
		return U.getByte(ref, addr+pos);
	}

	private void checkRange(long off, int len) { if ((off|len) < 0 || (off+len) > (long)this.length*compSize) throw new ArrayIndexOutOfBoundsException("off="+off+",len="+len+",cap="+this.length); }
}