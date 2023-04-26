package roj.asm.cst;

import roj.io.IOUtil;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstUTF extends Constant {
	Object data;

	public CstUTF() {}
	public CstUTF(String s) { data = s; }

	CstUTF(Object b) { data = b; }

	public String str() {
		if (data instanceof byte[]) data = IOUtil.SharedCoder.get().decode(((byte[]) data));
		return data.toString();
	}

	@Override
	public void write(DynByteBuf w) {
		w.put(Constant.UTF);
		if (data instanceof byte[]) w.putShort(((byte[]) data).length).write(((byte[]) data));
		else w.writeUTF(data.toString());
	}

	int _length() {
		if (data instanceof byte[]) return ((byte[]) data).length;
		return DynByteBuf.byteCountDioUTF(data.toString());
	}

	public String toString() { return super.toString() + ' ' + str(); }

	@Override
	public byte type() { return Constant.UTF; }

	public int hashCode() { return 1 + str().hashCode(); }

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstUTF)) return false;
		CstUTF ref = (CstUTF) o;
		return this.str().equals(ref.str());
	}
}