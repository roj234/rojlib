package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValInt extends AnnVal {
	public AnnValInt(char type, int value) {
		this.type = (byte) type;
		this.value = value;
	}

	public final byte type;
	public int value;

	public int asInt() { return value; }
	public float asFloat() { return value; }
	public long asLong() { return value; }
	public double asDouble() { return value; }

	public byte type() { return type; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put(type).putShort(cp.getIntId(value)); }
	public String toString() { return String.valueOf(value); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValInt anInt = (AnnValInt) o;

		if (type != anInt.type) return false;
		return value == anInt.value;
	}

	@Override
	public int hashCode() {
		int result = type;
		result = 31 * result + value;
		return result;
	}
}