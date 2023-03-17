package roj.config.exch;

import roj.config.NBTParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.config.serial.Structs;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TByteArray extends CList {
	private class Bytes extends AbstractList<CEntry> {
		TByte cache = new TByte((byte) 0);

		@Override
		public CEntry get(int index) {
			cache.value = data[index];
			return cache;
		}

		@Override
		public CEntry set(int index, CEntry element) {
			cache.value = data[index];
			data[index] = (byte) element.asInteger();
			return cache;
		}

		@Override
		public int size() {
			return data.length;
		}
	}

	public byte[] data;

	public TByteArray(byte[] data) {
		super(null);
		list = new Bytes();
		this.data = data;
	}

	//    重复使用一个对象会出问题么
	//    Immutable TByte ?
	//    出了问题再说吧

	@Override
	public Object unwrap() {
		return data;
	}

	@Override
	public byte getNBTType() {
		return NBTParser.BYTE_ARRAY;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.putInt(data.length).write(data);
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) (((1 + Type.Int1.ordinal()) << 4))).putVarInt(data.length, false).write(data);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.putAscii(Integer.toString(data.length)).put((byte) ':').put(data);
	}
}
