package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.anno.AnnVal;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AnnotationDefault extends Attribute {
	public AnnVal val;

	public AnnotationDefault(AnnVal val) { this.val = val; }
	public AnnotationDefault(DynByteBuf r, ConstantPool pool) { val = AnnVal.parse(pool, r); }
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) { val.toByteArray(pool, w); }

	public final String name() { return "AnnotationDefault"; }
	public String toString() { return name()+": "+val; }
}