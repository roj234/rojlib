package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstDouble extends Constant {
	public double value;

	public CstDouble(double value) {
		this.value = value;
	}

	@Override
	public void write(DynByteBuf w) {
		w.put(Constant.DOUBLE).putDouble(value);
	}

	@Override
	public byte type() {
		return Constant.DOUBLE;
	}

	public String toString() {
		return super.toString() + " : " + value;
	}

	public int hashCode() {
		long l = Double.doubleToRawLongBits(value);
		return (int) (l ^ (l >>> 32));
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstDouble && ((CstDouble) o).value == this.value;
	}
}