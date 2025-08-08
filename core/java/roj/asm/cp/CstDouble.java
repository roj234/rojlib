package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstDouble extends Constant {
	public double value;

	public CstDouble(double value) { this.value = value; }
	@Override public byte type() { return DOUBLE; }
	@Override void write(DynByteBuf w) {w.put(DOUBLE).putDouble(value);}

	@Override public String toString() {return Double.toString(value).concat("d");}
	@Override public String getEasyCompareValue() {return Double.toString(value);}

	public int hashCode() {return Long.hashCode(Double.doubleToRawLongBits(value)) * 0x133111EB;}
	public boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstDouble n && Double.doubleToRawLongBits(n.value) == Double.doubleToRawLongBits(value);
	}
}