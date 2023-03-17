package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Field implements FieldNode, AttributeReader {
	public Field(int accesses, String name, String type) {
		this.accesses = (char) accesses;
		this.name = name;
		this.type = TypeHelper.parseField(type);
	}

	public Field(int accesses, String name, Type type) {
		this.accesses = (char) accesses;
		this.name = name;
		this.type = type;
	}

	public Field(ConstantData data, RawField field) {
		this(field.accesses, field.name.getString(), field.type.getString());

		AttributeList al = field.attributesNullable();
		if (al != null && !al.isEmpty()) {
			attributes = new AttributeList(al);
			Parser.withParsedAttribute(data.cp, this);
		}
	}

	public Field(java.lang.reflect.Field f) {
		this.name = f.getName();
		this.accesses = (char) f.getModifiers();
		this.type = TypeHelper.parseField(TypeHelper.class2asm(f.getType()));
	}

	@Override
	public Attribute parseAttribute(ConstantPool pool, DynByteBuf r, String name, int length) {
		switch (name) {
			case "RuntimeVisibleTypeAnnotations":
			case "RuntimeInvisibleTypeAnnotations": return new TypeAnnotations(name, r, pool);
			// 字段泛型签名
			case "Signature": return Signature.parse(((CstUTF) pool.get(r)).getString());
			// 字段注解
			case "RuntimeVisibleAnnotations":
			case "RuntimeInvisibleAnnotations": return new Annotations(name, r, pool);
			// static final型‘常量’的默认值
			case "ConstantValue": return new ConstantValue(pool.get(r));
			case "Synthetic":
			case "Deprecated":
			default: return length < 0 ? null : new AttrUnknown(name, r.slice(length));
		}
	}

	public String name;
	public Type type;
	public char accesses;

	private AttributeList attributes;

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(accesses).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(TypeHelper.getField(type)));

		if (attributes == null) {
			w.putShort(0);
		} else {
			attributes.toByteArray(w, pool);
		}
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void name(ConstantPool cp, String name) {
		this.name = name;
	}

	@Override
	public String rawDesc() {
		return TypeHelper.getField(type);
	}

	@Override
	public void rawDesc(ConstantPool cp, String rawDesc) {
		type = TypeHelper.parseField(rawDesc);
	}

	@Override
	public int type() {
		return Parser.FTYPE_FULL;
	}

	@Override
	public void accessFlag(int flag) {
		this.accesses = (char) flag;
	}

	@Override
	public char accessFlag() {
		return accesses;
	}

	@Override
	public Type fieldType() {
		return type;
	}

	@Override
	public void fieldType(Type type) {
		this.type = type;
	}

	RawField i_downgrade(ConstantPool cw) {
		RawField f = new RawField(accesses, cw.getUtf(name), cw.getUtf(rawDesc()));
		if (attributes == null) return f;

		AttributeList fAttr = f.attributes();
		fAttr.ensureCapacity(attributes.size());
		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attributes.size(); i++) {
			fAttr.add(AttrUnknown.downgrade(cw, w, this.attributes.get(i)));
		}
		return f;
	}

	@Override
	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}

	public AttributeList attributes() {
		return attributes == null ? attributes = new AttributeList() : attributes;
	}

	@Nullable
	@Override
	public AttributeList attributesNullable() {
		return attributes;
	}

	public Annotations getAnnotations() {
		return attributes == null ? null : (Annotations) attributes.getByName(Annotations.VISIBLE);
	}

	public Annotations getInvisibleAnnotations() {
		return attributes == null ? null : (Annotations) attributes.getByName(Annotations.INVISIBLE);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		if (getAnnotations() != null) sb.append("    ").append(getAnnotations()).append('\n');
		if (getInvisibleAnnotations() != null) sb.append("    ").append(getInvisibleAnnotations()).append('\n');

		sb.append("    ");
		Signature signature = (Signature) attrByName("Signature");
		AccessFlag.toString(accesses, AccessFlag.TS_FIELD, sb).append(signature != null ? signature : type).append(' ').append(name);

		if (attributes != null) {
			ConstantValue constant = (ConstantValue) attributes.getByName("ConstantValue");
			if (constant != null) {
				sb.append(" = ").append(constant.c);
			}

			sb.append('\n');
			for (int i = 0; i < attributes.size(); i++) {
				Attribute attr = attributes.get(i);
				switch (attr.name) {
					case Annotations.VISIBLE:
					case Annotations.INVISIBLE:
					case "ConstantValue": continue;
				}
				sb.append("      ").append(attr).append('\n');
			}
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}
}