package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.util.AttributeKey;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrString extends Attribute {
	private static final int SOURCE_ID = NAMED_ID.getInt("SourceFile");

	private final byte name;
	public String value;

	public AttrString(AttributeKey<AttrString> key, String value) {
		this.name = (byte) NAMED_ID.getInt(key.name);
		this.value = value;
	}
	public AttrString(String name, String value) {
		this.name = (byte) NAMED_ID.getInt(name);
		this.value = value;
	}

	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) { w.putShort(name==SOURCE_ID ? pool.getUtfId(value) : pool.getClassId(value)); }

	public String name() { return NAMED_ID.get(name); }
	public String toString() { return name()+": "+value; }
}