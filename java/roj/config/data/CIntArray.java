package roj.config.data;

import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public final class CIntArray extends CList {
	private final class Ints extends AbstractList<CEntry> {
		//    重复使用一个对象会出问题么
		//    Immutable TByte ?
		//    出了问题再说吧
		private final CInteger ref = new CInteger(0);

		public int size() { return data.length; }
		public CEntry get(int i) {
			ref.value = data[i];
			return ref;
		}
		public CEntry set(int i, CEntry e) {
			ref.value = data[i];
			data[i] = e.asInteger();
			return ref;
		}
	}

	public int[] data;

	public CIntArray(int[] data) {
		super(null);
		list = new Ints();
		this.data = data;
	}

	public void accept(CVisitor ser) { ser.value(data); }
	public Object rawDeep() { return data; }

	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((((1 + Type.INTEGER.ordinal()) << 4))).putVUInt(data.length);
		for (int i : data) w.putInt(i);
	}
}