package roj.config.data;

import roj.config.serial.CVisitor;

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
			value[i] = (byte) e.asInt();
			return ref;
		}
	}

	public byte[] value;

	public CByteArray(byte[] value) {
		super(null);
		elements = new Bytes();
		this.value = value;
	}

	public byte[] toByteArray() {return value.clone();}

	public void accept(CVisitor visitor) { visitor.value(value); }
	public Object unwrap() { return value; }
}