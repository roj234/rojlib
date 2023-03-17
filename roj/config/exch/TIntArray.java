package roj.config.exch;

import roj.config.NBTParser;
import roj.config.data.CEntry;
import roj.config.data.CInteger;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.config.serial.Structs;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TIntArray extends CList {
	private class Ints extends AbstractList<CEntry> {
		CInteger cache = new CInteger(0);

		@Override
		public CEntry get(int index) {
			cache.value = data[index];
			return cache;
		}

		@Override
		public CEntry set(int index, CEntry element) {
			cache.value = data[index];
			data[index] = element.asInteger();
			return cache;
		}

		@Override
		public int size() {
			return data.length;
		}
	}

	public int[] data;

	public TIntArray(int[] data) {
		super(null);
		list = new Ints();
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
		return NBTParser.INT_ARRAY;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.writeInt(data.length);
		for (int i : data) {
			w.writeInt(i);
		}
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) (((1 + Type.INTEGER.ordinal()) << 4))).putVarInt(data.length, false);
		for (int i : data) {
			w.putInt(i);
		}
	}
}
