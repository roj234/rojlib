package roj.asm.tree.attr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.tree.RawNode;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AttrRecord extends Attribute {
	public AttrRecord() { variables = new SimpleList<>(); }
	public AttrRecord(DynByteBuf r, ConstantPool cp) {
		int len = r.readUnsignedShort();
		variables = new SimpleList<>(len);
		while (len-- > 0) {
			Val rd = new Val();
			variables.add(rd);

			rd.name = ((CstUTF) cp.get(r)).str();
			rd.type = ((CstUTF) cp.get(r)).str();
			int len1 = r.readUnsignedShort();
			if (len1 > 0) {
				rd.attributes = new AttributeList(len1);
				while (len1-- > 0) {
					String name0 = ((CstUTF) cp.get(r)).str();
					rd.attributes.i_direct_add(Parser.attr(rd, cp, name0, r.slice(r.readInt()), Parser.RECORD_ATTR));
				}
			}
		}
	}

	public List<Val> variables;

	@Override
	public boolean isEmpty() { return variables.isEmpty(); }

	@Override
	public String name() { return "Record"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
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

	public static final class Val implements RawNode {
		public String name, type;

		// Signature
		// RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
		// RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
		AttributeList attributes;

		@Override public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
		@Nullable
		@Override public AttributeList attributesNullable() { return attributes; }
		@Override public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedKey<T> type) { return Parser.parseAttribute(this,cp,type,attributes,Parser.RECORD_ATTR); }
		@Override public char modifier() {return Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL;}
		@Override public String name() { return name; }
		@Override public void name(String name) {this.name = name;}
		@Override public String rawDesc() { return type; }
		@Override public void rawDesc(String rawDesc) {this.type = rawDesc;}
	}
}