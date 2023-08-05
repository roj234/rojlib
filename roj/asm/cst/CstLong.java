package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstLong extends Constant {
	public long value;

	public CstLong(long value) { this.value = value; }
	@Override
	void write(DynByteBuf w) { w.put(LONG).putLong(value); }
	@Override
	public byte type() { return LONG; }

	public final String toString() { return super.toString() + " : " + value; }

	@Override
	public String getEasyReadValue() { return Long.toString(value).concat("L"); }
	@Override
	public String getEasyCompareValue() { return Long.toString(value); }

	public final int hashCode() { long l = value; return (int) ~(l ^ (l >>> 32)); }

	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstLong && ((CstLong) o).value == this.value;
	}
}