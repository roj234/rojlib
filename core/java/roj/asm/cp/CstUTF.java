package roj.asm.cp;

import roj.text.CharList;
import roj.text.FastCharset;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstUTF extends Constant {
	Object data;

	CstUTF() {}
	CstUTF(Object b) {data = b;}
	CstUTF(String b) {data = b;ConstantPool.verifyUtf(b);}

	public String str() {
		block: {
			DynByteBuf in;
			if (data.getClass() == byte[].class) {
				in = ByteList.wrap((byte[]) data);
			} else if (data instanceof DynByteBuf) {
				in = (DynByteBuf) data;
			} else {
				break block;
			}

			var out = new CharList();
			int rPos = in.rIndex;
			FastCharset.UTF8().decodeFixedIn(in, in.readableBytes(), out);
			in.rIndex = rPos;
			data = out.toStringAndFree();
		}

		return data.toString();
	}

	@Override
	public void write(DynByteBuf w) {
		w.put(Constant.UTF);
		if (data.getClass() == byte[].class) {
			byte[] b = (byte[]) data;
			w.putShort(b.length).write(b);
		} else if (data instanceof DynByteBuf b) {
			w.putShort(b.readableBytes()).put(b);
		} else w.writeUTF(data.toString());
	}

	int _length() {
		if (data.getClass() == byte[].class) return ((byte[]) data).length;
		if (data instanceof DynByteBuf) return ((DynByteBuf) data).readableBytes();
		return DynByteBuf.byteCountDioUTF(data.toString());
	}

	public String toString() { return super.toString() + ' ' + str(); }

	@Override
	public byte type() { return Constant.UTF; }

	@Override
	public Constant clone() { str(); return super.clone(); }

	public int hashCode() { return 1 + str().hashCode(); }

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstUTF)) return false;
		CstUTF ref = (CstUTF) o;
		return this.str().equals(ref.str());
	}
}