package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrString extends Attribute {
	public static final String SOURCE = "SourceFile";
	private static final int SOURCE_ID = NAMED_ID.getInt(SOURCE);

	private final byte name;
	public String value;

	public AttrString(String name, String value) {
		this.name = (byte) NAMED_ID.getInt(name);
		this.value = value;
	}
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) { w.putShort(name==SOURCE_ID ? pool.getUtfId(value) : pool.getClassId(value)); }

	public String name() { return NAMED_ID.get(name); }
	public String toString() { return name()+": "+value; }
}