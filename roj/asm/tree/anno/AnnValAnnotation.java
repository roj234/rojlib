package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValAnnotation extends AnnVal {
	public AnnValAnnotation(Annotation value) {
		this.value = value;
	}

	public Annotation value;

	@Override
	public Annotation asAnnotation() {
		return value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		value.toByteArray(pool, w.put((byte) ANNOTATION));
	}

	public String toString() {
		return value.toString();
	}

	@Override
	public byte type() {
		return ANNOTATION;
	}
}