package roj.config.data;

import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class CLongArray extends CList {
	private final class Longs extends AbstractList<CEntry> {
		private final CLong ref = new CLong(0);

		public int size() { return data.length; }
		public CEntry get(int index) {
			ref.value = data[index];
			return ref;
		}
		public CEntry set(int index, CEntry element) {
			ref.value = data[index];
			data[index] = element.asLong();
			return ref;
		}
	}

	public long[] data;

	public CLongArray(long[] data) {
		super(null);
		list = new Longs();
		this.data = data;
	}

	public void accept(CVisitor ser) { ser.value(data); }
	public long[] rawDeep() { return data; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((((1 + Type.LONG.ordinal()) << 4))).putVUInt(data.length);
		for (long i : data) w.putLong(i);
	}
}