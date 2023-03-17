package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstLong extends Constant {
	public long value;

	public CstLong(long value) {
		this.value = value;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(Constant.LONG).putLong(value);
	}

	@Override
	public byte type() {
		return Constant.LONG;
	}

	public final String toString() {
		return super.toString() + " : " + value;
	}

	public final int hashCode() {
		long l = value;
		return (int) ~(l ^ (l >>> 32));
	}

	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstLong && ((CstLong) o).value == this.value;
	}
}