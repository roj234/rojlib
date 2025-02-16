package roj.asm.attr;

import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class MethodParameters extends Attribute {
	public MethodParameters() { flags = new SimpleList<>(); }
	public MethodParameters(DynByteBuf r, ConstantPool pool) {
		int len = r.readUnsignedByte();
		var params = this.flags = new SimpleList<>(len);
		while (len-- > 0) {
			var utf = (CstUTF) pool.get(r);
			params.add(new MethodParam(utf == null ? null : utf.str(), r.readChar()));
		}
	}

	public final SimpleList<MethodParam> flags;

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.put(flags.size());
		for (int i = 0; i < flags.size(); i++) {
			var mp = flags.get(i);
			w.putShort(mp.name == null ? 0 : pool.getUtfId(mp.name)).putShort(mp.flag);
		}
	}

	@Override
	public String name() { return "MethodParameters"; }

	public String toString() {
		StringBuilder sb = new StringBuilder("MethodParameters: \n");
		for (int i = 0; i < flags.size(); i++) {
			MethodParam e = flags.get(i);
			sb.append("    Name: ").append(e.name).append("\n    Access: ");
			Opcodes.showModifiers(e.flag, Opcodes.ACC_SHOW_PARAM, sb);
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

	public int getFlag(int i, int def) { return flags.size() > i ? flags.get(i).flag : def; }
}