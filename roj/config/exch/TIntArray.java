package roj.config.exch;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.CInteger;
import roj.config.data.CList;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class TIntArray extends CList {
	private final class Ints extends AbstractList<CEntry> {
		private final CInteger ref = new CInteger(0);

		@Override
		public CEntry get(int i) {
			ref.value = data[i];
			return ref;
		}

		@Override
		public CEntry set(int i, CEntry e) {
			ref.value = data[i];
			data[i] = e.asInteger();
			return ref;
		}

		@Override
		public int size() { return data.length; }
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

	public Object unwrap() { return data; }

	public byte getNBTType() { return NBTParser.INT_ARRAY; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) (((1 + Type.INTEGER.ordinal()) << 4))).putVUInt(data.length);
		for (int i : data) w.putInt(i);
	}

	public void forEachChild(CVisitor ser) { ser.value(data); }
}
