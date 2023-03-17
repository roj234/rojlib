package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrClassRef extends Attribute {
	public AttrClassRef(String name) {
		super(name);
	}

	public AttrClassRef(String name, DynByteBuf r, ConstantPool pool) {
		super(name);
		this.owner = ((CstClass) pool.get(r)).name().str();
	}

	public String owner;

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getClassId(owner));
	}

	public String toString() {
		return name + ": " + owner;
	}
}