package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
	protected Attribute(String name) {
		this.name = name;
	}

	public final String name;

	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(name)).putInt(0);
		int i = w.wIndex();
		toByteArray1(w, pool);
		w.putInt(i - 4, w.wIndex() - i);
	}

	 protected void toByteArray1(DynByteBuf w, ConstantPool cp) {
		throw new UnsupportedOperationException(getClass().getName());
	}

	public boolean isEmpty() {
		return false;
	}

	public abstract String toString();

	public DynByteBuf getRawData() {
		throw new UnsupportedOperationException(getClass().getName());
	}
}