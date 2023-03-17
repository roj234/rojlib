package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValArray extends AnnVal {
	public AnnValArray(List<AnnVal> value) {
		this.value = value;
	}

	public List<AnnVal> value;

	@Override
	public List<AnnVal> asArray() {
		return value;
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.put((byte) ARRAY).putShort(value.size());
		for (int i = 0; i < value.size(); i++) {
			value.get(i).toByteArray(pool, w);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("{");
		if (value.size() > 0) {
			for (int i = 0; i < value.size(); i++) {
				AnnVal val = value.get(i);
				sb.append(val).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
		}
		return sb.append('}').toString();
	}

	@Override
	public byte type() {
		return ARRAY;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AnnValArray array = (AnnValArray) o;

		return value.equals(array.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}
}