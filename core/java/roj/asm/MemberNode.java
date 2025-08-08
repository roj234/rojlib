package roj.asm;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.attr.AttributeList;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstNameAndType;
import roj.asm.cp.CstUTF;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/9/24 13:54
 */
public abstract class MemberNode implements Member {
	@MagicConstant(flags = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC |
			ACC_SYNCHRONIZED | ACC_ABSTRACT | ACC_VARARGS | ACC_STRICT | ACC_NATIVE | ACC_BRIDGE |
			ACC_TRANSIENT | ACC_VOLATILE)
	public char modifier;
	Object name, desc;
	public AttributeList attributes;

	/**
	 * @return this
	 */
	public abstract MemberNode parsed(ConstantPool cp);
	public final void unparsed(ConstantPool cp) {
		if (attributes == null) return;

		var buf = AsmCache.buf();
		for (int i = 0; i < attributes.size(); i++) {
			attributes.set(i, UnparsedAttribute.serialize(cp, buf, attributes.get(i)));
		}
		AsmCache.buf(buf);
	}

	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(modifier)
		 .putShort(name.getClass() == CstUTF.class ? pool.fit((CstUTF) name) : pool.getUtfId(name.toString()))
		 .putShort(desc.getClass() == CstUTF.class ? pool.fit((CstUTF) desc) : pool.getUtfId(rawDesc()));

		if (attributes == null) w.putShort(0);
		else attributes.toByteArray(w, pool);
	}

	public final AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public final AttributeList attributesNullable() { return attributes; }

	public final String name() { return name.getClass() == String.class ? name.toString() : ((CstUTF) name).str(); }
	public final void name(String name) { this.name = name.toString(); } // null check

	// public String rawDesc();
	public void rawDesc(String desc) { this.desc = desc.toString(); } // null check

	public final char modifier() { return modifier; }
	public final void modifier(int flag) { modifier = (char) flag; }

	public final boolean matches(CstNameAndType nat) {
		CstUTF u = nat.name();
		if (u != name && !u.str().equals(name())) return false;
		u = nat.rawDesc();
		return u == desc || u.str().equals(rawDesc());
	}

	public final boolean matches(MemberDescriptor desc) { return name().equals(desc.name) && desc.rawDesc.isEmpty() || rawDesc().equals(desc.rawDesc); }
}