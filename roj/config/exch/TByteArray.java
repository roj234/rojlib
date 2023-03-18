package roj.config.exch;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TByteArray extends CList {
	private final class Bytes extends AbstractList<CEntry> {
		private final TByte ref = new TByte((byte) 0);

		@Override
		public CEntry get(int i) {
			ref.value = data[i];
			return ref;
		}

		@Override
		public CEntry set(int i, CEntry e) {
			ref.value = data[i];
			data[i] = (byte) e.asInteger();
			return ref;
		}

		@Override
		public int size() { return data.length; }
	}

	public byte[] data;

	public TByteArray(byte[] data) {
		super(null);
		list = new Bytes();
		this.data = data;
	}

	public Object unwrap() { return data; }

	public byte getNBTType() { return NBTParser.BYTE_ARRAY; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) (((1 + Type.Int1.ordinal()) << 4))).putVUInt(data.length).write(data);
	}

	public void toB_encode(DynByteBuf w) { w.putAscii(Integer.toString(data.length)).put((byte) ':').put(data); }

	public void forEachChild(CVisitor ser) { ser.value(data); }
}
