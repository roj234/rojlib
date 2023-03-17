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

	public AttrCodeWriter(ConstantPool cp, MethodNode mn) {
		super("Code", new ByteList());
		cw = new CodeWriter();
		cw.init(super.getRawData(), cp);
		cw.mn = mn;
		this.mn = mn;
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.put(getRawData());
	}

	@Override
	public DynByteBuf getRawData() {
		cw.finish();
		return super.getRawData();
	}
}
