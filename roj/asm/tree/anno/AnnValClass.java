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
	public AnnValClass(String className) {
		this.value = TypeHelper.parseField(className);
	}

	public Type value;

	@Override
	public Type asClass() {
		return value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) CLASS).putShort(pool.getUtfId(TypeHelper.getField(value)));
	}

	public String toString() {
		return value + ".class";
	}

	@Override
	public byte type() {
		return CLASS;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValClass aClass = (AnnValClass) o;

		return value.equals(aClass.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}
}