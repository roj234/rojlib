package roj.asm.visitor;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrUnknown;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 19:52
 */
public class AttrCodeWriter extends AttrUnknown {
	public final CodeWriter cw;
	public final MethodNode mn;

	public AttrCodeWriter(ConstantPool cp, MethodNode mn) { this(cp, mn, new CodeWriter()); }
	public AttrCodeWriter(ConstantPool cp, MethodNode mn, CodeWriter cw) {
		super("Code", null);
		ByteList buf = new ByteList();
		this.cw = cw;
		cw.init(buf, cp);
		cw.mn = mn;
		this.mn = mn;
		setRawData(buf);
	}

	@Override
	public DynByteBuf getRawData() {
		cw.finish();
		return super.getRawData();
	}
}
