package roj.asm.attr;

import org.jetbrains.annotations.Nullable;
import roj.asm.FieldNode;
import roj.asm.Member;
import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.collect.ArrayList;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class RecordAttribute extends Attribute {
	public static final int ATTR_RECORD = 3;

	public RecordAttribute() { fields = new ArrayList<>(); }
	public RecordAttribute(DynByteBuf r, ConstantPool cp) {
		int len = r.readUnsignedShort();
		fields = new ArrayList<>(len);
		while (len-- > 0) {
			Field rd = new Field();
			fields.add(rd);

			rd.name = ((CstUTF) cp.get(r)).str();
			rd.type = ((CstUTF) cp.get(r)).str();
			int len1 = r.readUnsignedShort();
			if (len1 > 0) {
				rd.attributes = new AttributeList(len1);
				while (len1-- > 0) {
					String name0 = ((CstUTF) cp.get(r)).str();
					rd.attributes._add(Attribute.parse(rd, cp, name0, r.slice(r.readInt()), ATTR_RECORD));
				}
			}
		}
	}

	public List<Field> fields;

	//Javac会生成空的该属性
	//@Override public boolean isEmpty() { return fields.isEmpty(); }
	@Override public String name() { return "Record"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(fields.size());
		for (int i = 0; i < fields.size(); i++) {
			Field r = fields.get(i);
			w.putShort(pool.getUtfId(r.name)).putShort(pool.getUtfId(r.type));
			if (r.attributes == null) w.putShort(0);
			else r.attributes.toByteArray(w, pool);
		}
	}

	public String toString() {
		if (fields.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			sb.append(fields.get(i)).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static final class Field implements Member {
		public String name, type;

		// Signature
		// RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
		// RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
		AttributeList attributes;

		public Field() {}
		public Field(String name, String type, AttributeList attributes) {
			this.name = name;
			this.type = type;
			this.attributes = attributes;
		}
		public static Field link(FieldNode field) {return new Field(field.name(), field.rawDesc(), field.attributes);}

		@Override public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
		@Nullable
		@Override public AttributeList attributesNullable() { return attributes; }
		@Override public <T extends Attribute> T getAttribute(ConstantPool cp, TypedKey<T> type) { return Attribute.parseSingle(this,cp,type,attributes, ATTR_RECORD); }
		@Override public char modifier() {return Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL;}
		@Override public String name() { return name; }
		@Override public void name(String name) {this.name = name;}
		@Override public String rawDesc() { return type; }
		@Override public void rawDesc(String rawDesc) {this.type = rawDesc;}
	}
}