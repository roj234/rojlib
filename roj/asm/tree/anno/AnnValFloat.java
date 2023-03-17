package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/2/7 20:39
 */
public final class AnnValFloat extends AnnVal {
	public AnnValFloat(float value) {
		this.value = value;
	}

	public float value;

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
		return (long) value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) FLOAT).putShort(pool.getFloatId(value));
	}

	public String toString() {
		return String.valueOf(value);
	}

	@Override
	public byte type() {
		return FLOAT;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValFloat aFloat = (AnnValFloat) o;

		return Float.compare(aFloat.value, value) == 0;
	}

	@Override
	public int hashCode() {
		return (value != +0.0f ? Float.floatToIntBits(value) : 0);
	}
}