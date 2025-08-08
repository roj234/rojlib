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
	private Object target;

	@Deprecated
	CstMethodHandle(byte kind, int refIndex) {
		this.kind = kind;
		this.target = refIndex;
	}
	public CstMethodHandle(@MagicConstant(valuesFromClass = BootstrapMethods.Kind.class) byte kind, CstRef target) {
		BootstrapMethods.Kind.validate(kind, target);
		this.kind = kind;
		this.target = target;
	}

	@Override
	public byte type() {return Constant.METHOD_HANDLE;}

	@Override
	public final void write(DynByteBuf w) {w.put(Constant.METHOD_HANDLE).put(kind).putShort(getRefIndex());}

	public final String toString() { return super.toString() + " Type: " + kind + ", Method: " + target; }

	public final int hashCode() { return (target.hashCode() << 3) | kind; }
	public final boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstMethodHandle handle)) return false;
		return handle.kind == this.kind && handle.getRefIndex() == this.getRefIndex();
	}

	public final CstRef getTarget() { return (CstRef) target; }
	int getRefIndex() {
		return target instanceof Constant ? ((Constant) target).index : ((Number) target).intValue();
	}

	public void setTarget(CstRef target) {
		if (target == null) throw new NullPointerException("ref");
		this.target = target;
	}

	@Override
	public final CstMethodHandle clone() {
		CstMethodHandle slf = (CstMethodHandle) super.clone();
		if (target instanceof Constant) slf.target = ((Constant) target).clone();
		return slf;
	}
}