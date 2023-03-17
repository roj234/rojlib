package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AnnValClass extends AnnVal {
	public AnnValClass(String className) { value = TypeHelper.parseField(className); }

	public Type value;

	public Type asClass() { return value; }

	public byte type() { return ANNOTATION_CLASS; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) ANNOTATION_CLASS).putShort(cp.getUtfId(TypeHelper.getField(value))); }
	public String toString() { return value + ".class"; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value.equals(((AnnValClass) o).value);
	}

	@Override
	public int hashCode() { return value.hashCode(); }
}