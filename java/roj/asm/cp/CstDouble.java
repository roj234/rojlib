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

	public int hashCode() {
		long l = Double.doubleToRawLongBits(value);
		return (int) (l ^ (l >>> 32));
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstDouble && Double.compare(((CstDouble) o).value, value) == 0;
	}
}