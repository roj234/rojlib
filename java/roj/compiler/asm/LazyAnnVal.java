package roj.compiler.asm;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.attr.AnnotationDefault;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/6/21 0021 17:39
 */
public final class LazyAnnVal extends AnnVal {
	public final AnnotationDefault ad;

	public LazyAnnVal(AnnotationDefault ad) {this.ad = ad;}

	@Override
	public byte type() {return ad.val.type();}
	@Override
	public void toByteArray(ConstantPool cp, DynByteBuf w) {ad.val.toByteArray(cp, w);}
	@Override
	public String toString() {return ad.val.toString();}
}