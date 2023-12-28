package roj.io.buf;

import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/8/3 0003 3:44
 */
public final class NativeArray {
	private static final boolean DEBUG = false;

	private final Object ref;
	private final long addr;
	private final int len;

	public NativeArray(Object ref, long addr, int len) {
		this.ref = ref;
		this.addr = addr;
		this.len = len;
	}

	public byte get(int i) {
		if (DEBUG) checkRange(i, 1);
		return u.getByte(ref, addr+i);
	}

	public void set(int i, int v) {
		if (DEBUG) checkRange(i, 1);
		u.putByte(ref, addr+i, (byte) v);
	}

	public void copyTo(int i, byte[] b, int off, int len) {
		if (DEBUG) { checkRange(i, len); ArrayUtil.checkRange(b, off, len); }
		u.copyMemory(ref, addr+i, b, (long)Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public void copyTo(int i, long addr, int len) {
		if (DEBUG) checkRange(i, len);
		u.copyMemory(this.addr+i, addr, len);
	}

	public void copyFrom(int i, byte[] b, int off, int len) {
		if (DEBUG) { checkRange(i, len); ArrayUtil.checkRange(b, off, len); }
		u.copyMemory(b, (long)Unsafe.ARRAY_BYTE_BASE_OFFSET+i, ref, addr+i, len);
	}
	public void copyFrom(int i, long addr, int len) {
		if (DEBUG) checkRange(i, len);
		u.copyMemory(addr, this.addr+i, len);
	}

	public int length() { return len; }

	private void checkRange(int i, int len) { if ((i|len) < 0 || (i+len) > this.len) throw new ArrayIndexOutOfBoundsException("off="+i+",len="+len+",cap="+this.len); }

	public DynByteBuf buffer() { return ref == null ? DirectByteList.wrap(addr, len) : ByteList.wrap((byte[]) ref, (int) (addr-Unsafe.ARRAY_BYTE_BASE_OFFSET), len); }
}