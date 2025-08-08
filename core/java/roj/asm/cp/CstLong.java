package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstLong extends Constant {
	public long value;

	public CstLong(long value) { this.value = value; }
	@Override public byte type() { return LONG; }
	@Override void write(DynByteBuf w) { w.put(LONG).putLong(value); }

	@Override public String toString() { return Long.toString(value).concat("L"); }
	@Override public String getEasyCompareValue() { return Long.toString(value); }

	public final int hashCode() {return Long.hashCode(value) * 0x1CE4E5B9;}
	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstLong n && n.value == value;
	}
}