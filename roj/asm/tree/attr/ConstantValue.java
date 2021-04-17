package roj.asm.tree.attr;

import roj.asm.cst.Constant;
import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ConstantValue extends Attribute {
	public ConstantValue(Constant c) {
		super("ConstantValue");
		this.c = c;
	}

	public Constant c;

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.reset(c).getIndex());
	}

	public String toString() {
		return "ConstantValue: " + c;
	}
}