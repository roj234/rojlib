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
		return CstNameAndType.parseNodeDesc(sb, clazz.name().str(), desc.name().str(), desc.getType().str());
	}

	public final String className() { return clazz.name().str(); }
	public final String descName() { return desc.name().str(); }
	public final String descType() { return desc.getType().str(); }

	public CstClass clazz() {
		return clazz;
	}
	public CstNameAndType desc() {
		return desc;
	}

	public final void clazz(CstClass clazz) {
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
		return 31 * (31 * desc.hashCode() + clazz.name().hashCode()) + type();
	}

	public final boolean equals(Object o) {
		return o instanceof CstRef && equals0((CstRef) o);
	}

	public final boolean equals0(CstRef ref) {
		if (ref == this) return true;
		if (ref.getClass() != getClass()) return false;
		return ref.clazz.name().equals(clazz.name()) && ref.desc.equals0(desc);
	}

	@Override
	public final CstRef clone() {
		CstRef slf = (CstRef) super.clone();
		slf.clazz = (CstClass) clazz.clone();
		slf.desc = desc.clone();
		return slf;
	}

	public boolean matches(String clazz, String name, String desc) {
		return this.clazz.name().str().equals(clazz) && this.desc.name().str().equals(name) && this.desc.getType().str().equals(desc);
	}
}