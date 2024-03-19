package roj.config.data;

import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class CByteArray extends CList {
	private final class Bytes extends AbstractList<CEntry> {
		private final CByte ref = new CByte((byte) 0);

		public int size() { return data.length; }
		public CEntry get(int i) {
			ref.value = data[i];
			return ref;
		}
		public CEntry set(int i, CEntry e) {
			ref.value = data[i];
			data[i] = (byte) e.asInteger();
			return ref;
		}
	}

	public byte[] data;

	public CByteArray(byte[] data) {
		super(null);
		list = new Bytes();
		this.data = data;
	}

	public void accept(CVisitor ser) { ser.value(data); }
	public byte[] rawDeep() { return data; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put((byte) (((1 + Type.Int1.ordinal()) << 4))).putVUInt(data.length).write(data); }
	public void toB_encode(DynByteBuf w) { w.putAscii(Integer.toString(data.length)).put((byte) ':').put(data); }
}