package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class MethodParameters extends Attribute {
	public static final String NAME = "MethodParameters";

	public MethodParameters() {
		super(NAME);
		flags = new SimpleList<>();
	}

	public MethodParameters(DynByteBuf r, ConstantPool pool) {
		super(NAME);
		flags = parse(r, pool);
	}

	public final SimpleList<MethodParam> flags;

	public static SimpleList<MethodParam> parse(DynByteBuf r, ConstantPool pool) {
		int len = r.readUnsignedByte();
		SimpleList<MethodParam> params = new SimpleList<>(len);
		while (len-- > 0) {
			String name = ((CstUTF) pool.get(r)).getString();
			params.add(new MethodParam(name, r.readChar()));
		}
		return params;
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.put((byte) flags.size());
		for (int i = 0; i < flags.size(); i++) {
			MethodParam e = flags.get(i);
			w.putShort(pool.getUtfId(e.name)).putShort(e.flag);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("MethodParameters: \n");
		for (int i = 0; i < flags.size(); i++) {
			MethodParam e = flags.get(i);
			sb.append("    Name: ").append(e.name).append("\n    Access: ");
			AccessFlag.toString(e.flag, AccessFlag.TS_PARAM, sb);
			sb.append('\n');
		}
		return sb.toString();
	}

	public static final class MethodParam {
		public String name;
		public char flag;

		public MethodParam(String name, char flag) {
			this.name = name;
			this.flag = flag;
		}
	}
}