package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValDouble extends AnnVal {
	public AnnValDouble(double v) { value = v; }

	public double value;

	public int asInt() { return (int) value; }
	public float asFloat() { return (float) value; }
	public long asLong() { return (long) value; }
	public double asDouble() { return value; }

	public byte type() { return Type.DOUBLE; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) Type.DOUBLE).putShort(cp.getDoubleId(value)); }
	public String toString() { return String.valueOf(value).concat("D"); }

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