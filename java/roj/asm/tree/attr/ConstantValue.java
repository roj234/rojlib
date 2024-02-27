package roj.asm.tree.attr;

import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ConstantValue extends Attribute {
	public ConstantValue(Constant c) { this.c = c; }

	public Constant c;

	@Override
	public String name() { return "ConstantValue"; }
	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) { w.putShort(pool.reset(c).getIndex()); }
	public String toString() { return "ConstantValue: " + c; }
}