package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.text.CharList;
import roj.util.AttributeKey;
import roj.util.DynByteBuf;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class FieldNode extends CNode {
	public FieldNode(int acc, String name, String type) { this(acc, (Object) name, type); }
	public FieldNode(int acc, String name, Type type) { this(acc, (Object) name, type); }
	public FieldNode(int acc, CstUTF name, CstUTF type) { this(acc, (Object) name, type); }
	private FieldNode(int acc, Object name, Object type) {
		this.modifier = (char) acc;
		this.name = name;
		this.desc = type;
	}
	public FieldNode copyDesc() { return new FieldNode(modifier, name, desc); }
	public FieldNode copy() {
		FieldNode inst = copyDesc();
		if (attributes != null) inst.attributes = new AttributeList(attributes);
		return inst;
	}

	public FieldNode(java.lang.reflect.Field f) {
		this.modifier = (char) f.getModifiers();
		this.name = f.getName();
		this.desc = TypeHelper.class2asm(f.getType());
	}

	public FieldNode parsed(ConstantPool cp) {
		if (attributes == null) return this;
		Parser.parseAttributes(this, cp, attributes, Signature.FIELD);
		return this;
	}
	public FieldNode unparsed(ConstantPool cp) {
		if (attributes == null) return this;

		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attributes.size(); i++) {
			attributes.set(i, AttrUnknown.downgrade(cp, w, attributes.get(i)));
		}
		return this;
	}

	public <T extends Attribute> T parsedAttr(ConstantPool cp, AttributeKey<T> type) { return Parser.parseAttribute(this,cp,type,attributes,Signature.FIELD); }

	public String rawDesc() { return desc.getClass() == CstUTF.class ? ((CstUTF) desc).str() : desc.getClass() == Type.class ? ((Type) desc).toDesc() : desc.toString(); }

	public Type fieldType() {
		if (desc.getClass() != Type.class) desc = TypeHelper.parseField(rawDesc());
		return (Type) desc;
	}
	public void fieldType(Type type) { this.desc = Objects.requireNonNull(type); }

	public String toString() { return toString(new CharList(), null, 4, false).toStringAndFree(); }
	public CharList toString(CharList sb, ConstantData owner, int prefix, boolean writeSignature) {
		ConstantPool cp = owner == null ? null : owner.cp;

		Annotations a;
		a = parsedAttr(cp, Attribute.RtAnnotations);
		if (a != null) a.toString(sb, prefix);
		a = parsedAttr(cp, Attribute.ClAnnotations);
		if (a != null) a.toString(sb, prefix);

		Signature sig = writeSignature ? parsedAttr(cp, Attribute.SIGNATURE) : null;
		Opcodes.showModifiers(modifier, Opcodes.ACC_SHOW_FIELD, sb.padEnd(' ', prefix)).append(sig != null ? sig : fieldType()).append(' ').append(name());

		ConstantValue cv = parsedAttr(cp, Attribute.ConstantValue);
		if (cv != null) sb.append(" = ").append(cv.c.getEasyReadValue());

		return sb.append(';');
	}
}