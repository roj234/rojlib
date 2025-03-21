package roj.asm.cp;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.attr.BootstrapMethods;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstMethodHandle extends Constant {
	public byte kind;
	private Object ref;

	@Deprecated
	CstMethodHandle(byte kind, int refIndex) {
		this.kind = kind;
		this.ref = refIndex;
	}
	public CstMethodHandle(@MagicConstant(valuesFromClass = BootstrapMethods.Kind.class) byte kind, CstRef ref) {
		this.kind = kind;
		this.ref = ref;
	}

	@Override
	public byte type() {return Constant.METHOD_HANDLE;}

	@Override
	public final void write(DynByteBuf w) {w.put(Constant.METHOD_HANDLE).put(kind).putShort(getRefIndex());}

	public final String toString() { return super.toString() + " Type: " + kind + ", Method: " + ref; }

	public final int hashCode() { return (ref.hashCode() << 3) | kind; }
	public final boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstMethodHandle handle)) return false;
		return handle.kind == this.kind && handle.getRefIndex() == this.getRefIndex();
	}

	public final CstRef getRef() { return (CstRef) ref; }
	int getRefIndex() {
		return ref instanceof Constant ? ((Constant) ref).index : ((Number) ref).intValue();
	}

	public void setRef(CstRef ref) {
		if (ref == null) throw new NullPointerException("ref");
		this.ref = ref;
	}

	@Override
	public final CstMethodHandle clone() {
		CstMethodHandle slf = (CstMethodHandle) super.clone();
		if (ref instanceof Constant) slf.ref = ((Constant) ref).clone();
		return slf;
	}
}