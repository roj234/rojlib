package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2020/10/21 22:45
 */
public class UnparsedAttribute extends Attribute {
	public static Attribute serialize(ConstantPool cw, DynByteBuf r, Attribute attr) {
		if (attr.getClass() == UnparsedAttribute.class) return attr;

		r.clear(); attr.toByteArrayNoHeader(r, cw);
		int length = r.readableBytes();
		return new UnparsedAttribute(attr.name(), length == 0 ? null : r.toByteArray());
	}

	private final Object name;
	private Object data;

	public UnparsedAttribute(String name, DynByteBuf data) {
		this.name = name;
		this.data = data;
	}
	public UnparsedAttribute(String name, byte[] data) {
		this.name = name;
		this.data = data;
	}
	public UnparsedAttribute(CstUTF name, Object data) {
		this.name = name;
		this.data = data;
	}

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(name.getClass() == CstUTF.class ? pool.fit((CstUTF) name) : pool.getUtfId(name.toString()));
		if (data == null) {
			w.putInt(0);
		} else if (data.getClass() == byte[].class) {
			byte[] b = (byte[]) data;
			w.putInt(b.length).put(b);
		} else {
			DynByteBuf b = (DynByteBuf) data;
			w.putInt(b.readableBytes()).put(b);
		}
	}

	@Override
	public String name() { return name.getClass() == CstUTF.class ? ((CstUTF) name).str() : name.toString(); }

	public String toString() { return name()+(data==null?"":": "+getRawData().dump()); }

	public DynByteBuf getRawData() { return data == null ? null : (DynByteBuf) (data.getClass() == byte[].class ? data = new ByteList((byte[]) data) : data); }
	public void setRawData(DynByteBuf data) { this.data = data; }
}