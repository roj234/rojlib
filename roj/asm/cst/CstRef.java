package roj.asm.cst;

import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class CstRef extends Constant {
	private CstClass clazz;
	private CstNameAndType desc;

	CstRef(CstClass c, CstNameAndType d) {
		this.clazz = c;
		this.desc = d;
	}

	CstRef() {}

	@Override
	public final void write(DynByteBuf w) {
		w.put(type()).putShort(clazz.getIndex()).putShort(desc.getIndex());
	}

	public final String toString() {
		CharList sb = new CharList().append(super.toString())
			.append(" 引用[").append(clazz.getIndex()).append(",").append(desc.getIndex()).append("] ");
		return CstNameAndType.parseNodeDesc(sb, clazz.getValue().getString(), desc.getName().getString(), desc.getType().getString());
	}

	public final String getClassName() {
		return clazz.getValue().getString();
	}

	public final void setClazz(CstClass clazz) {
		if (clazz == null)
			throw new NullPointerException("clazz");
		this.clazz = clazz;
	}

	public final void desc(CstNameAndType desc) {
		if (desc == null)
			throw new NullPointerException("desc");
		this.desc = desc;
	}

	public final int hashCode() {
		return (clazz.hashCode() << 16) ^ desc.hashCode() * type();
	}

	public final boolean equals(Object o) {
		return o instanceof CstRef && equals0((CstRef) o);
	}

	public final boolean equals0(CstRef ref) {
		if (ref == this) return true;
		if (ref.getClass() != getClass()) return false;
		return ref.clazz.equals0(clazz) && ref.desc.equals0(desc);
	}

	public CstClass getClazz() {
		return clazz;
	}

	public CstNameAndType desc() {
		return desc;
	}

	@Override
	public final CstRef clone() {
		CstRef slf = (CstRef) super.clone();
		slf.clazz = (CstClass) clazz.clone();
		slf.desc = desc.clone();
		return slf;
	}

	public boolean matches(String clazz, String name, String desc) {
		return this.clazz.getValue().getString().equals(clazz) && this.desc.getName().getString().equals(name) && this.desc.getType().getString().equals(desc);
	}
}