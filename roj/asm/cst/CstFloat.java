package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstFloat extends Constant {
	public float value;

	public CstFloat(float value) { this.value = value; }
	@Override
	void write(DynByteBuf w) { w.put(FLOAT).putFloat(value); }
	@Override
	public byte type() { return FLOAT; }

	public final String toString() { return super.toString() + " : " + value; }

	@Override
	public String getEasyReadValue() { return Float.toString(value).concat("f"); }
	@Override
	public String getEasyCompareValue() { return Float.toString(value); }

	public final int hashCode() { return Float.floatToRawIntBits(value); }

	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstFloat && ((CstFloat) o).value == this.value;
	}
}