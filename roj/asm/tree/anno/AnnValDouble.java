package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValDouble extends AnnVal {
	public AnnValDouble(double value) {
		this.value = value;
	}

	public double value;

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
		return (float) value;
	}

	@Override
	public long asLong() {
		return (long) value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) DOUBLE).putShort(pool.getDoubleId(value));
	}

	public String toString() {
		return String.valueOf(value);
	}

	@Override
	public byte type() {
		return DOUBLE;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValDouble aDouble = (AnnValDouble) o;

		return Double.compare(aDouble.value, value) == 0;
	}

	@Override
	public int hashCode() {
		long temp = Double.doubleToLongBits(value);
		return (int) (temp ^ (temp >>> 32));
	}
}