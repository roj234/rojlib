package roj.config.exch;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CLong;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TLongArray extends CList {
	private final class Longs extends AbstractList<CEntry> {
		private final CLong ref = new CLong(0);

		public CEntry get(int index) {
			ref.value = data[index];
			return ref;
		}

		public CEntry set(int index, CEntry element) {
			ref.value = data[index];
			data[index] = element.asLong();
			return ref;
		}

		public int size() { return data.length; }
	}

	public long[] data;

	public TLongArray(long[] data) {
		super(null);
		list = new Longs();
		this.data = data;
	}

	public Object unwrap() { return data; }

	public byte getNBTType() { return NBTParser.LONG_ARRAY; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) (((1 + Type.LONG.ordinal()) << 4))).putVUInt(data.length);
		for (long i : data) w.putLong(i);
	}

	public void forEachChild(CVisitor ser) { ser.value(data); }
}
