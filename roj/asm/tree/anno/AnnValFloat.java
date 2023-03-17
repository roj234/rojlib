package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/2/7 20:39
 */
public final class AnnValFloat extends AnnVal {
	public AnnValFloat(float v) { this.value = v; }

	public float value;

	public int asInt() { return (int) value; }
	public float asFloat() { return value; }
	public long asLong() { return (long) value; }
	public double asDouble() { return value; }

	public byte type() { return Type.FLOAT; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) Type.FLOAT).putShort(cp.getFloatId(value)); }
	public String toString() { return String.valueOf(value); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValFloat aFloat = (AnnValFloat) o;

		return Float.compare(aFloat.value, value) == 0;
	}

	@Override
	public int hashCode() { return (value != +0.0f ? Float.floatToIntBits(value) : 0); }
}