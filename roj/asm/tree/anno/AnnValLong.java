package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/2/7 20:39
 */
public final class AnnValLong extends AnnVal {
	public AnnValLong(long value) {
		this.value = value;
	}

	public long value;

	@Override
	public int asInt() {
		return (int) value;
	}

	@Override
	public double asDouble() {
		return value;
	}

	@Override
	public float asFloat() {
		return value;
	}

	@Override
	public long asLong() {
		return value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) LONG).putShort(pool.getLongId(value));
	}

	public String toString() {
		return String.valueOf(value);
	}

	@Override
	public byte type() {
		return LONG;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValLong aLong = (AnnValLong) o;

		return value == aLong.value;
	}

	@Override
	public int hashCode() {
		return (int) (value ^ (value >>> 32));
	}
}