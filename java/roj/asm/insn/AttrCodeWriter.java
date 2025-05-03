package roj.asm.insn;

import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 19:52
 */
public class AttrCodeWriter extends Attribute {
	public final CodeWriter cw;
	private final ByteList data;

	public AttrCodeWriter(ConstantPool cp, MethodNode mn) {this(cp, mn, new CodeWriter());}
	public AttrCodeWriter(ConstantPool cp, MethodNode mn, CodeWriter cw) {
		ByteList buf = new ByteList();
		this.cw = cw;
		cw.init(buf, cp, mn);
		this.data = buf;
	}

	@Override public String name() {return "Code";}
	@Override public String toString() {return "AttrCodeWriter: "+cw;}
	@Override public DynByteBuf getRawData() {cw.finish();return data;}

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId("Code"));

		DynByteBuf buf = getRawData();
		if (buf.readableBytes() == 0) throw new IllegalStateException("You may only serialize this once");
		w.putInt(buf.readableBytes()).put(buf);
		data._free();
	}
}