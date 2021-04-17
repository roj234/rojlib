package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstMethodHandle extends Constant {
	public byte kind;

	private int refIndex;
	private CstRef ref;

	public CstMethodHandle(byte kind, int refIndex) {
		this.kind = kind;
		this.refIndex = refIndex;
	}

	@Override
	public byte type() {
		return Constant.METHOD_HANDLE;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(Constant.METHOD_HANDLE).put(kind).putShort(getRefIndex());
	}

	public final String toString() {
		return super.toString() + " Type: " + kind + ", Method: " + ref;
	}

	public final int hashCode() {
		return (ref.hashCode() << 3) | kind;
	}

	public final boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstMethodHandle)) return false;
		CstMethodHandle ref = (CstMethodHandle) o;
		return ref.kind == this.kind && ref.getRefIndex() == this.getRefIndex();
	}

	public final CstRef getRef() {
		return ref;
	}

	public void setRef(CstRef ref) {
		if (ref == null) throw new NullPointerException("ref");
		this.ref = ref;
		this.refIndex = ref.getIndex();
	}

	public int getRefIndex() {
		return ref == null ? refIndex : ref.getIndex();
	}

	@Override
	public final CstMethodHandle clone() {
		CstMethodHandle slf = (CstMethodHandle) super.clone();
		if (ref != null) slf.ref = ref.clone();
		return slf;
	}
}