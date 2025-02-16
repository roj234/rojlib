package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstFloat extends Constant {
	public float value;

	public CstFloat(float value) {this.value = value;}
	@Override public byte type() {return FLOAT;}
	@Override void write(DynByteBuf w) {w.put(FLOAT).putFloat(value);}

	@Override public String toString() {return Float.toString(value).concat("f");}
	@Override public String getEasyCompareValue() {return Float.toString(value);}

	public final int hashCode() {return Float.floatToRawIntBits(value);}

	public final boolean equals(Object o) {
		if (o == this) return true;
		return o instanceof CstFloat && Float.compare(((CstFloat) o).value, value) == 0;
	}
}