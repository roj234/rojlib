package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.anno.AnnVal;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AnnotationDefault extends Attribute {
	public static final String NAME = "AnnotationDefault";

	public AnnotationDefault(DynByteBuf r, ConstantPool pool) {
		super(NAME);
		def = AnnVal.parse(pool, r);
	}

	public AnnVal def;

	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		def.toByteArray(pool, w);
	}

	public String toString() {
		return "AnnotationDefault: " + def;
	}
}