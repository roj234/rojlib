package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstDynamic;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class InvokeDynInsnNode extends IInvokeInsnNode {
	public InvokeDynInsnNode() {
		super(Opcodes.INVOKEDYNAMIC);
	}

	public InvokeDynInsnNode(CstDynamic ref, int type) {
		super(Opcodes.INVOKEDYNAMIC);
		this.tableIdx = ref.tableIdx;
		this.name = ref.desc().getName().getString();
		this.rawDesc = ref.desc().getType().getString();
	}

	@Override
	public int nodeType() {
		return T_INVOKE_DYNAMIC;
	}

	@Override
	protected boolean validate() {
		return code == Opcodes.INVOKEDYNAMIC;
	}

	/**
	 * bootstrap table index
	 */
	public char tableIdx;

	@Override
	public void serialize(CodeWriter cw) {
		cw.invokeDyn(tableIdx, name, rawDesc(), 0);
	}

	@Override
	public int nodeSize(int prevBci) {
		return 5;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString()).append(" #").append((int) tableIdx).append(' ').append(returnType).append(" ?").append('.').append(name).append('(');

		List<Type> params = parameters();
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

	@Override
	public void fullDesc(String desc) {
		throw new UnsupportedOperationException("InvokeDyn does not support 'classed' descriptor");
	}

	@Override
	public String fullDesc() {
		throw new UnsupportedOperationException("InvokeDyn does not support 'classed' descriptor");
	}
}