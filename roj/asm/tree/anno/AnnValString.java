package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValString extends AnnVal {
	public AnnValString(String value) {
		this.value = value;
	}

	public String value;

	@Override
	public String asString() {
		return value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) STRING).putShort(pool.getUtfId(value));
	}

	public String toString() {
		return '"' + value + '"';
	}

	@Override
	public byte type() {
		return STRING;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValString string = (AnnValString) o;

		return value.equals(string.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}
}