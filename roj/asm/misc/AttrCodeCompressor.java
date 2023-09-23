package roj.asm.misc;

import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.ConstantPool;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 19:52
 */
public class AttrCodeCompressor extends Attribute {
	private final byte[] data;

	public static void compress(ConstantData data) {
		ConstantPool cpw = new ConstantPool(data.cp.array().size());
		CodeVisitor smallerLdc = new CodeVisitor() {
			protected void ldc(byte code, Constant c) { cpw.reset(c); }
		};

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			Attribute code = methods.get(i).attrByName("Code");
			if (code != null) smallerLdc.visit(data.cp, Parser.reader(code));
		}

		CodeWriter cw = new CodeWriter();
		ByteList bw = new ByteList();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.attrByName("Code");
			if (code != null) {
				cw.init(bw, cpw);
				cw.mn = mn; // for UNINITIAL_THIS
				cw.visit(data.cp, code.getRawData());
				cw.finish();

				mn.putAttr(new AttrCodeCompressor(bw.toByteArray()));
				bw.clear();
			}
		}

		// will not process AttrCodeCompressor
		data.parsed();
		data.cp = cpw;
	}

	public AttrCodeCompressor(byte[] array) { this.data = array; }
	public void toByteArray(DynByteBuf w, ConstantPool pool) { w.putShort(pool.getUtfId("Code")).putInt(data.length).put(data); }
	public String name() { return "Code"; }
	public String toString() { return "<AttrCodeCompressor>"; }
}