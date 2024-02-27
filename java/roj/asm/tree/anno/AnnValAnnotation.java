package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValAnnotation extends AnnVal {
	public AnnValAnnotation(Annotation v) { value = v; }

	public Annotation value;

	public Annotation asAnnotation() { return value; }

	public byte type() { return ANNOTATION; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { value.toByteArray(cp, w.put((byte) ANNOTATION)); }
	public String toString() { return value.toString(); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AnnValAnnotation that)) return false;

		return value.equals(that.value);
	}

	@Override
	public int hashCode() { return value.hashCode(); }
}