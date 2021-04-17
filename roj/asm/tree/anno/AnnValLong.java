package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/2/7 20:39
 */
public final class AnnValLong extends AnnVal {
	public AnnValLong(long v) { value = v; }

	public long value;

	public int asInt() { return (int) value; }
	public float asFloat() { return value; }
	public long asLong() { return value; }
	public double asDouble() { return value; }

	public byte type() { return Type.LONG; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) Type.LONG).putShort(cp.getLongId(value)); }
	public String toString() { return String.valueOf(value); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value == ((AnnValLong) o).value;
	}

	@Override
	public int hashCode() { return (int) (value ^ (value >>> 32)); }
}