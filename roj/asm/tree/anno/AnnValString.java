package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.config.word.ITokenizer;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/1/9 14:23
 */
public final class AnnValString extends AnnVal {
	AnnValString(String v) { value = v; }

	public String value;

	public String asString() { return value; }

	public byte type() { return STRING; }

	public void toByteArray(ConstantPool cp, DynByteBuf w) { w.put((byte) STRING).putShort(cp.getUtfId(value)); }
	public String toString() { return '"' + ITokenizer.addSlashes(value) + '"'; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return value.equals(((AnnValString) o).value);
	}

	@Override
	public int hashCode() { return value.hashCode(); }
}