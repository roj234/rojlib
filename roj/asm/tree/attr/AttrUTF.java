package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrUTF extends Attribute {
	public static final String SIGNATURE = "Signature", SOURCE = "SourceFile";

	public static AttrUTF Source(String v) {
		return new AttrUTF(SOURCE, v);
	}

	public AttrUTF(String name) {
		super(name);
	}

	public AttrUTF(String name, DynByteBuf r, ConstantPool pool) {
		super(name);
		this.value = ((CstUTF) pool.get(r)).getString();
	}

	public String value;

	public AttrUTF(String name, String value) {
		super(name);
		this.value = value;
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(value));
	}

	public String toString() {
		return name + ": " + value;
	}
}