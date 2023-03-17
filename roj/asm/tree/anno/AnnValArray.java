package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValArray extends AnnVal {
	public AnnValArray(List<AnnVal> v) { value = v; }

	public List<AnnVal> value;

	@Override
	public List<AnnVal> asArray() { return value; }

	public byte type() { return Type.ARRAY; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) {
		w.put((byte) Type.ARRAY).putShort(value.size());
		for (int i = 0; i < value.size(); i++) {
			value.get(i).toByteArray(cp, w);
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value.equals(((AnnValArray) o).value);
	}

	@Override
	public int hashCode() { return value.hashCode(); }
}