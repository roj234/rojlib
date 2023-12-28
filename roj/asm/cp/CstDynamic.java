package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstDynamic extends Constant {
	public char tableIdx;
	private final boolean method;

	private CstNameAndType desc;

	public CstDynamic(boolean method, int tableIndex, CstNameAndType desc) {
		this.method = method;
		this.tableIdx = (char) tableIndex;
		this.desc = desc;
	}

	public final void setDesc(CstNameAndType desc) {
		if (desc == null) throw new NullPointerException("desc");
		this.desc = desc;
	}

	@Override
	public byte type() {
		return method ? Constant.INVOKE_DYNAMIC : Constant.DYNAMIC;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(type()).putShort(tableIdx).putShort(desc.getIndex());
	}

	public final String toString() {
		return super.toString() + " T#" + (int) tableIdx + ", //" + desc + "]";
	}

	@Override
	public String getEasyReadValue() { return toString(); }

	public final CstNameAndType desc() {
		return desc;
	}

	public final int hashCode() {
		return ((desc.hashCode() * 31 + tableIdx) * 31) * type();
	}

	public final boolean equals(Object o) {
		if (o == this) return true;
		if (o == null || o.getClass() != getClass()) return false;
		CstDynamic ref = (CstDynamic) o;
		return ref.type() == this.type() && ref.tableIdx == this.tableIdx && ref.desc.equals0(desc);
	}

	@Override
	public final CstDynamic clone() {
		CstDynamic slf = (CstDynamic) super.clone();
		if (desc != null) slf.desc = desc.clone();
		return slf;
	}
}