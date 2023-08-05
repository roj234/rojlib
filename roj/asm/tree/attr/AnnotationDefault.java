package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.anno.AnnVal;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AnnotationDefault extends Attribute {
	public AnnVal val;

	public AnnotationDefault(DynByteBuf r, ConstantPool pool) { val = AnnVal.parse(pool, r); }
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) { val.toByteArray(pool, w); }

	public final String name() { return "AnnotationDefault"; }
	public String toString() { return name()+": "+val; }
}