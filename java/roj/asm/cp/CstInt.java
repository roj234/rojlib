package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstInt extends Constant {
	public int value;

	public CstInt(int value) { this.value = value; }
	@Override
	void write(DynByteBuf w) { w.put(INT).putInt(value); }
	@Override
	public byte type() { return INT; }

	public final String toString() { return super.toString() + " : " + value; }

	@Override
	public String getEasyReadValue() { return Integer.toString(value); }
	@Override
	public String getEasyCompareValue() { return Integer.toString(value); }

	public final int hashCode() { return value; }

	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstInt && ((CstInt) o).value == this.value;
	}
}