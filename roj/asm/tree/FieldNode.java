package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

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
		this.access = (char) acc;
		this.name = name;
		this.desc = type;
	}
	public FieldNode copyDesc() { return new FieldNode(access, name, desc); }
	public FieldNode copy() {
		FieldNode inst = copyDesc();
		if (attributes != null) inst.attributes = new AttributeList(attributes);
		return inst;
	}

	public FieldNode(java.lang.reflect.Field f) {
		this.access = (char) f.getModifiers();
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

	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this,cp,type,attributes,Signature.FIELD); }

	public String rawDesc() { return desc.getClass() == CstUTF.class ? ((CstUTF) desc).str() : desc.getClass() == Type.class ? ((Type) desc).toDesc() : desc.toString(); }

	public Type fieldType() {
		if (desc.getClass() != Type.class) desc = TypeHelper.parseField(rawDesc());
		return (Type) desc;
	}
	public void fieldType(Type type) { this.desc = Objects.requireNonNull(type); }

	public String toString() { return toString(new CharList(), null).toStringAndFree(); }
	public CharList toString(CharList sb, ConstantData owner) {
		ConstantPool cp = owner == null ? null : owner.cp;

		Attribute a;
		a = parsedAttr(cp, Attribute.RtAnnotations);
		if (a != null) sb.append(a).append('\n');
		a = parsedAttr(cp, Attribute.ClAnnotations);
		if (a != null) sb.append(a).append('\n');

		Signature sig = parsedAttr(cp, Attribute.SIGNATURE);
		int acc = access;
		if (owner != null && (owner.access&AccessFlag.INTERFACE) != 0) {
			// default is public static ?
		}
		AccessFlag.toString(acc, AccessFlag.TS_FIELD, sb).append(sig != null ? sig : fieldType()).append(' ').append(name());

		ConstantValue cv = parsedAttr(cp, Attribute.ConstantValue);
		if (cv != null) sb.append(" = ").append(cv.c.getEasyReadValue());

		return sb.append(';');
	}
}