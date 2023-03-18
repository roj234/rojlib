package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Field implements FieldNode {
	public Field(int access, String name, String type) {
		this.access = (char) access;
		this.name = name;
		this.type = type;
	}

	public Field(int access, String name, Type type) {
		this.access = (char) access;
		this.name = name;
		this.type = type;
	}

	public Field(ConstantData data, RawField field) {
		this(field.access, field.name.getString(), field.type.getString());

		AttributeList al = field.attributesNullable();
		if (al != null && !al.isEmpty()) {
			attributes = new AttributeList(al);
			Parser.parseAttributes(this, data.cp, attributes, Signature.FIELD);
		}
	}

	public Field(java.lang.reflect.Field f) {
		this.name = f.getName();
		this.access = (char) f.getModifiers();
		this.type = TypeHelper.class2asm(f.getType());
	}

	public String name;
	private Object type;

	public char access;

	private AttributeList attributes;

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(access).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(rawDesc()));

		if (attributes == null) {
			w.putShort(0);
		} else {
			attributes.toByteArray(w, pool);
		}
	}

	RawField i_downgrade(ConstantPool cw) {
		RawField f = new RawField(access, cw.getUtf(name), cw.getUtf(rawDesc()));
		if (attributes == null) return f;

		AttributeList fAttr = f.attributes();
		fAttr.ensureCapacity(attributes.size());
		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attributes.size(); i++) {
			fAttr.add(AttrUnknown.downgrade(cw, w, this.attributes.get(i)));
		}
		return f;
	}

	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this,cp,type,attributes,Signature.FIELD); }
	public Attribute attrByName(String name) { return attributes == null ? null : (Attribute) attributes.getByName(name); }

	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public AttributeList attributesNullable() { return attributes; }

	public String name() { return name; }
	public void name(ConstantPool cp, String name) { this.name = name; }

	public String rawDesc() { return type instanceof Type ? ((Type) type).toDesc() : type.toString(); }
	public void rawDesc(ConstantPool cp, String desc) { type = Objects.requireNonNull(desc); }

	public Type fieldType() {
		if (!(type instanceof Type)) type = TypeHelper.parseField(type.toString());
		return (Type) type;
	}
	public void fieldType(Type type) { this.type = Objects.requireNonNull(type); }

	public char modifier() { return access; }
	public void modifier(int flag) { access = (char) flag; }

	public int type() { return Parser.FTYPE_FULL; }

	public String toString() {
		StringBuilder sb = new StringBuilder();

		Attribute a;
		a = parsedAttr(null, Attribute.RtAnnotations);
		if (a != null) sb.append("    ").append(a).append('\n');
		a = parsedAttr(null, Attribute.ClAnnotations);
		if (a != null) sb.append("    ").append(a).append('\n');

		sb.append("    ");
		Signature sig = parsedAttr(null, Attribute.SIGNATURE);
		AccessFlag.toString(access, AccessFlag.TS_FIELD, sb).append(sig != null ? sig : fieldType()).append(' ').append(name);

		ConstantValue cv = parsedAttr(null, Attribute.ConstantValue);
		if (cv != null) sb.append(" = ").append(cv.c);

		return sb.toString();
	}
}