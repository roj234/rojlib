package roj.asm.cst;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstUTF extends Constant {
	private String data;

	public CstUTF() {}

	public CstUTF(String data) {
		this.data = data;
	}

	public String getString() {
		return this.data;
	}

	public void setString(String s) {
		// noinspection all
		this.data = s.toString();
	}

	@Override
	public void write(DynByteBuf w) {
		w.put(Constant.UTF).putUTF(data);
	}

	public String toString() {
		return super.toString() + ' ' + data;
	}

	@Override
	public byte type() {
		return Constant.UTF;
	}

	public int hashCode() {
		return 1 + data.hashCode();
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstUTF)) return false;
		CstUTF ref = (CstUTF) o;
		return this.data.equals(ref.data);
	}
}