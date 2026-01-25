package roj.asm.cp;

import roj.optimizer.FastVarHandle;
import roj.reflect.Telescope;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.Tokenizer;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
@FastVarHandle
public final class CstUTF extends Constant {
	// concurrent | TODO pre-process attributes in Lava Compiler
	private static final VarHandle DATA = Telescope.lookup().findVarHandle(CstUTF.class, "data", Object.class);
	Object data;

	CstUTF() {}
	CstUTF(Object b) {data = b;}
	CstUTF(String b) {data = b;ConstantPool.verifyUtfLength(b);}

	public String str() {
		Object data = this.data; // non-volatile read

		while (true) {
			DynByteBuf in;

			if (data != null) {
				if (data.getClass() == byte[].class) {
					in = ByteList.wrap((byte[]) data);
				} else if (data instanceof DynByteBuf) {
					in = (DynByteBuf) data;
				} else {
					break;
				}

				if (DATA.compareAndSet(this, data, null)) {
					var out = new CharList();
					int rPos = in.rIndex;
					FastCharset.UTF8().decodeFixedIn(in, in.readableBytes(), out);
					in.rIndex = rPos;
					data = out.toStringAndFree();

					DATA.setVolatile(this, data);
				}
			}

			Thread.onSpinWait();
			data = DATA.getVolatile(this);
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

	int byteLength() {
		if (data.getClass() == byte[].class) return ((byte[]) data).length;
		if (data instanceof DynByteBuf) return ((DynByteBuf) data).readableBytes();
		return DynByteBuf.countJavaUTF(data.toString());
	}

	public String toString() { return super.toString() + ' ' + Tokenizer.escape(str()); }

	@Override
	public byte type() { return Constant.UTF; }

	@Override
	public Constant clone() { str(); return super.clone(); }

	public int hashCode() { return 1 + str().hashCode(); }

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof CstUTF ref)) return false;
		return this.str().equals(ref.str());
	}
}