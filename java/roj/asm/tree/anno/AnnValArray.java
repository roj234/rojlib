package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.asm.type.Type;
import roj.text.CharList;
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
		CharList sb = new CharList().append('{');
		if (value.size() > 0) {
			int i = 0;
			while (true) {
				sb.append(value.get(i));
				if (++i == value.size()) break;
				sb.append(", ");
			}
		}
		return sb.append('}').toStringAndFree();
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