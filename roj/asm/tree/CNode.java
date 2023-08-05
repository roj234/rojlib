package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.mapper.util.Desc;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/9/24 0024 13:54
 */
public abstract class CNode implements RawNode {
	public char access;
	Object name, desc;
	public AttributeList attributes;

	/**
	 * @return this
	 */
	public abstract CNode parsed(ConstantPool cp);

	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(access)
		 .putShort(name.getClass() == CstUTF.class ? pool.reset((CstUTF) name).getIndex() : pool.getUtfId(name.toString()))
		 .putShort(desc.getClass() == CstUTF.class ? pool.reset((CstUTF) desc).getIndex() : pool.getUtfId(rawDesc()));

		if (attributes == null) w.putShort(0);
		else attributes.toByteArray(w, pool);
	}

	public final Attribute attrByName(String name) { return attributes == null ? null : (Attribute) attributes.getByName(name); }

	public final AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public final AttributeList attributesNullable() { return attributes; }

	public final String name() { return name.getClass() == String.class ? name.toString() : ((CstUTF) name).str(); }
	public final void name(String name) { this.name = name.toString(); } // null check

	// public String rawDesc();
	public void rawDesc(String desc) { this.desc = desc.toString(); } // null check

	public final char modifier() { return access; }
	public final void modifier(int flag) { access = (char) flag; }

	public final boolean descMatch(CstNameAndType nat) {
		CstUTF u = nat.name();
		if (u != name && !u.str().equals(name())) return false;
		u = nat.getType();
		return u == desc || u.str().equals(rawDesc());
	}

	public final boolean descMatch(Desc desc) { return name().equals(desc.name) && desc.param.isEmpty() || rawDesc().equals(desc.param); }
}
