package roj.asm.cst;

import roj.io.IOUtil;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstUTF extends Constant {
	private Object data;

	public CstUTF() {}
	public CstUTF(String s) { data = s; }

	CstUTF(Object b) { data = b; }
	final void i_setData(Object b) { data = b; }

	public String getString() {
		if (data instanceof byte[]) data = IOUtil.SharedCoder.get().decode(((byte[]) data));
		return data.toString();
	}

	public void setString(String s) {
		// noinspection all
		data = s.toString();
	}

	@Override
	public void write(DynByteBuf w) {
		w.put(Constant.UTF);
		if (data instanceof byte[]) w.putShort(((byte[]) data).length).write(((byte[]) data));
		else w.writeUTF(data.toString());
	}

	public String toString() { return super.toString() + ' ' + getString(); }

	@Override
	public byte type() { return Constant.UTF; }

	public int hashCode() {
		return 1 + getString().hashCode();
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstUTF)) return false;
		CstUTF ref = (CstUTF) o;
		return this.getString().equals(ref.getString());
	}
}