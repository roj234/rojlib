package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
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
		return new AttrUnknown(attr.name(), new ByteList(tmp.toByteArray()));
	}

	public AttrUnknown(String name, DynByteBuf data) {
		super(name);
		this.data = data;
	}
	public AttrUnknown(CstUTF name, DynByteBuf data) {
		super(name);
		this.data = data;
	}

	private DynByteBuf data;

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		DynByteBuf data = getRawData();
		w.putShort(name instanceof CstUTF ? pool.reset((CstUTF) name).getIndex() : pool.getUtfId(name.toString()))
		 .putInt(data.readableBytes()).put(data);
	}

	@Override
	public String name() { return name instanceof CstUTF ? ((CstUTF) name).getString() : name.toString(); }


	public String toString() { return name() + ": " + data.dump(); }

	public DynByteBuf getRawData() { return data; }
	public void setRawData(DynByteBuf data) { this.data = data; }
}
