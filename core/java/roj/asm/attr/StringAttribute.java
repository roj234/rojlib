package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class StringAttribute extends Attribute {
	private static final int SOURCE_ID = NAMED_ID.getByValue("SourceFile");
	private static final int MODULE_TARGET_ID = NAMED_ID.getByValue("ModuleTarget");

	private final byte name;
	public String value;

	public StringAttribute(TypedKey<StringAttribute> key, String value) {
		this.name = (byte) NAMED_ID.getByValue(key.name);
		this.value = value;
	}
	public StringAttribute(String name, String value) {
		this.name = (byte) NAMED_ID.getByValue(name);
		this.value = value;
	}

	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) { w.putShort(name==SOURCE_ID||name==MODULE_TARGET_ID ? pool.getUtfId(value) : pool.getClassId(value)); }

	public String name() { return NAMED_ID.get(name); }
	public String toString() { return name()+": "+value; }
}