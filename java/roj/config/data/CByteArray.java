package roj.config.data;

import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

import java.util.AbstractList;

/**
 * @author Roj233
 * @since 2022/5/17 1:43
 */
public class CByteArray extends CList {
	private final class Bytes extends AbstractList<CEntry> {
		private final CByte ref = new CByte((byte) 0);

		public int size() { return value.length; }
		public CEntry get(int i) {
			ref.value = value[i];
			return ref;
		}
		public CEntry set(int i, CEntry e) {
			ref.value = value[i];
			value[i] = (byte) e.asInteger();
			return ref;
		}
	}

	public byte[] value;

	public CByteArray(byte[] value) {
		super(null);
		list = new Bytes();
		this.value = value;
	}

	public void accept(CVisitor ser) { ser.value(value); }
	public byte[] rawDeep() { return value; }

	public void toB_encode(DynByteBuf w) { w.putAscii(Integer.toString(value.length)).put((byte) ':').put(value); }
}