package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2020/10/21 22:45
 */
public class AttrUnknown extends Attribute {
	public static Attribute downgrade(ConstantPool cw, DynByteBuf tmp, Attribute attr) {
		tmp.clear();
		attr.toByteArray1(tmp, cw);
		return new AttrUnknown(attr.name, new ByteList(tmp.toByteArray()));
	}

	public AttrUnknown(String name, DynByteBuf data) {
		super(name);
		this.data = data;
	}

	private DynByteBuf data;

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.put(data);
	}

	public String toString() {
		return name + ": " + data.dump();
	}

	public DynByteBuf getRawData() {
		return data;
	}

	public void setRawData(DynByteBuf data) {
		this.data = data;
	}
}
