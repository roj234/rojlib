package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstDynamic;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class InvokeDynInsnNode extends InsnNode {
	public InvokeDynInsnNode(CstDynamic ref, int type) {
		super(Opcodes.INVOKEDYNAMIC);
		this.tableIdx = ref.tableIdx;
		this.name = ref.desc().getName().getString();
		this.desc = ref.desc().getType().getString();
	}

	public InvokeDynInsnNode(int idx, String name, String desc, int type) {
		super(Opcodes.INVOKEDYNAMIC);
		this.tableIdx = (char) idx;
		this.name = name;
		this.desc = desc;
	}

	public String name, desc;
	/**
	 * bootstrap table index
	 */
	public char tableIdx;

	@Override
	public int nodeType() {
		return T_INVOKE_DYNAMIC;
	}

	@Override
	protected boolean validate() {
		return code == Opcodes.INVOKEDYNAMIC;
	}

	@Override
	public void serialize(CodeWriter cw) {
		cw.invokeDyn(tableIdx, name, desc, 0);
	}

	@Override
	public int nodeSize(int prevBci) {
		return 5;
	}

	public String toString() {
		List<Type> params = TypeHelper.parseMethod(desc);
		StringBuilder sb = new StringBuilder(super.toString()).append(" #").append((int) tableIdx).append(' ').append(params.remove(params.size()-1)).append(" ?").append('.').append(name).append('(');

		if (!params.isEmpty()) {
			int i = 0;
			while (true) {
				Type par = params.get(i++);
				sb.append(par);
				if (i == params.size()) break;
				sb.append(", ");
			}
		}
		return sb.append(')').toString();
	}
}