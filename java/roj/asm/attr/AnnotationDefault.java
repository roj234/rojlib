package roj.asm.attr;

import roj.asm.annotation.AnnVal;
import roj.asm.cp.ConstantPool;
import roj.config.data.CEntry;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public final class AnnotationDefault extends Attribute {
	public CEntry val;

	public AnnotationDefault(CEntry val) {this.val = val;}
	public AnnotationDefault(DynByteBuf r, ConstantPool pool) {val = AnnVal.parse(pool, r);}
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {AnnVal.serialize(val, w, pool);}

	public final String name() {return "AnnotationDefault";}
	public String toString() {return name()+": "+val;}
}