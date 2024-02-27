package roj.asm.cp;

import roj.util.DynByteBuf;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class CstRefUTF extends Constant {
	private CstUTF value;

	CstRefUTF(CstUTF v) { value = v; }
	CstRefUTF(String v) { value = new CstUTF(Objects.requireNonNull(v, "value"));}
	CstRefUTF() {}

	public CstUTF name() { return value; }

	public final void setValue(CstUTF value) {this.value = Objects.requireNonNull(value, "value");}

	@Override
	public final void write(DynByteBuf w) {w.put(type()).putShort(value.getIndex());}

	public final String toString() {return super.toString() + " 引用["+value.getIndex()+"] " + value.str();}

	public final int hashCode() {return 31 * value.hashCode() + type();}
	public final boolean equals(Object o) {return o instanceof CstRefUTF && equals0((CstRefUTF) o);}

	public final boolean equals0(CstRefUTF o) {
		if (o == this) return true;
		if (o.getClass() != getClass()) return false;
		return value.str().equals(o.value.str());
	}

	@Override
	public final CstRefUTF clone() {
		CstRefUTF slf = (CstRefUTF) super.clone();
		slf.value = (CstUTF) value.clone();
		return slf;
	}
}