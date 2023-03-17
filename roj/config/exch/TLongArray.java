package roj.config.exch;

import roj.config.NBTParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CLong;
import roj.config.data.Type;
import roj.config.serial.Structs;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TLongArray extends CList {
	private class Longs extends AbstractList<CEntry> {
		CLong cache = new CLong(0);

		@Override
		public CEntry get(int index) {
			cache.value = data[index];
			return cache;
		}

		@Override
		public CEntry set(int index, CEntry element) {
			cache.value = data[index];
			data[index] = element.asLong();
			return cache;
		}

		@Override
		public int size() {
			return data.length;
		}
	}

	public long[] data;

	public TLongArray(long[] data) {
		super(null);
		list = new Longs();
		this.data = data;
	}

	@Override
	public Object unwrap() {
		return data;
	}

	@Override
	public byte getNBTType() {
		return NBTParser.LONG_ARRAY;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.writeInt(data.length);
		for (long i : data) {
			w.writeLong(i);
		}
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) (((1 + Type.LONG.ordinal()) << 4))).putVarInt(data.length, false);
		for (long i : data) {
			w.putLong(i);
		}
	}
}
