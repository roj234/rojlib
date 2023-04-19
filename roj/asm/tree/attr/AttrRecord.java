package roj.asm.tree.attr;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.FieldNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AttributeList;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AttrRecord extends Attribute {
	public AttrRecord() {
		super("Record");
	}

	public AttrRecord(DynByteBuf r, ConstantPool pool) {
		super("Record");

		int len = r.readUnsignedShort();
		variables = new SimpleList<>(len);
		while (len-- > 0) {
			Val rd = new Val();
			variables.add(rd);

			rd.name = ((CstUTF) pool.get(r)).getString();
			rd.type = ((CstUTF) pool.get(r)).getString();
			int len1 = r.readUnsignedShort();
			if (len1 > 0) {
				rd.attributes = new AttributeList(len1);
				while (len1-- > 0) {
					String name0 = ((CstUTF) pool.get(r)).getString();
					rd.attributes.i_direct_add(new AttrUnknown(name0, r.slice(r.readInt())));
				}
			}
		}
	}

	public List<Val> variables;

	@Override
	public boolean isEmpty() {
		return variables.isEmpty();
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(variables.size());
		for (int i = 0; i < variables.size(); i++) {
			Val r = variables.get(i);
			w.putShort(pool.getUtfId(r.name)).putShort(pool.getUtfId(r.type));
			if (r.attributes == null) w.putShort(0);
			else r.attributes.toByteArray(w, pool);
		}
	}

	public String toString() {
		if (variables.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < variables.size(); i++) {
			sb.append(variables.get(i)).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static final class Val implements FieldNode {
		public String name, type;

		AttributeList attributes;
		// Signature
		// RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
		// RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations

		@Override
		public AttributeList attributes() {
			return attributes == null ? attributes = new AttributeList() : attributes;
		}

		@Nullable
		@Override
		public AttributeList attributesNullable() {
			return attributes;
		}

		@Override
		public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) {
			return Parser.parseAttribute(this,cp,type,attributes, Parser.RECORD_ATTR);
		}

		@Override
		public char modifier() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int type() {
			return Parser.RECORD_ATTR;
		}

		@Override
		public Type fieldType() {
			return TypeHelper.parseField(type);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String rawDesc() {
			return type;
		}
	}
}