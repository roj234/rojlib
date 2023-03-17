package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstNameAndType extends Constant {
	private CstUTF name, type;

	public CstNameAndType() {}

	public CstNameAndType(CstUTF nameIndex, CstUTF typeIndex) {
		this.name = nameIndex;
		this.type = typeIndex;
	}

	@Override
	public byte type() {
		return Constant.NAME_AND_TYPE;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(Constant.NAME_AND_TYPE).putShort(name.getIndex()).putShort(type.getIndex());
	}

	public final String toString() {
		return super.toString() + " " + name.getString() + " (" + name.getIndex() + ")" + ':' + type.getString() + " (" + type.getIndex() + ")";
	}

	public final int hashCode() {
		return (name.hashCode() << 16) ^ type.hashCode();
	}

	public final boolean equals(Object o) {
		return o instanceof CstNameAndType && equals0((CstNameAndType) o);
	}

	public final boolean equals0(CstNameAndType ref) {
		if (ref == this) return true;
		return ref.name.equals(name) && ref.type.equals(type);
	}

	public final CstUTF getName() {
		return name;
	}

	public final void setName(CstUTF name) {
		if (name == null)
			throw new NullPointerException("name");
		this.name = name;
	}

	public final CstUTF getType() {
		return type;
	}

	public final void setType(CstUTF type) {
		if (type == null)
			throw new NullPointerException("type");
		this.type = type;
	}

	@Override
	public final CstNameAndType clone() {
		CstNameAndType slf = (CstNameAndType) super.clone();
		slf.name = (CstUTF) name.clone();
		slf.type = (CstUTF) type.clone();
		return slf;
	}
}