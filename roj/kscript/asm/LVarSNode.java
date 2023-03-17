package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2021/5/27 12:52
 */
public final class LVarSNode extends LVarGNode {
	public LVarSNode() {}

	@Override
	public Opcode getCode() {
		return Opcode.PUT_VAR;
	}

	@Override
	public Node exec(Frame frame) {
		frame.parents[ctxId].putIdx(varId, frame.pop().memory(0));
		return next;
	}

	@Override
	public String toString() {
		return "lvt[" + ctxId + '.' + varId + "] = pop()";
	}
}
