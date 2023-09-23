package roj.asm.visitor;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 19:52
 */
public class AttrCodeWriter extends Attribute {
	private final ByteList data;
	public final CodeWriter cw;
	public final MethodNode mn;

	public AttrCodeWriter(ConstantPool cp, MethodNode mn) { this(cp, mn, new CodeWriter()); }
	public AttrCodeWriter(ConstantPool cp, MethodNode mn, CodeWriter cw) {
		ByteList buf = new ByteList();
		this.cw = cw;
		cw.init(buf, cp);
		cw.mn = mn;
		this.mn = mn;
		this.data = buf;
	}

	@Override
	public String name() { return "Code"; }
	@Override
	public String toString() { return "AttrCodeWriter: "+cw; }

	@Override
	public DynByteBuf getRawData() {
		cw.finish();
		return data;
	}

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId("Code"));

		DynByteBuf buf = getRawData();
		w.putInt(buf.readableBytes()).put(buf);
	}
}
