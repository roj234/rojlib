package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2021/5/27 12:52
 */
public class LVarGNode extends Node {
	int ctxId, varId;

	public LVarGNode() {}

	@Override
	public Node exec(Frame frame) {
		frame.push(frame.parents[ctxId].getIdx(varId).memory(-1));
		return next;
	}

	@Override
	public String toString() {
		return "_load_ lvt[" + ctxId + '.' + varId + ']';
	}

	@Override
	public Opcode getCode() {
		return Opcode.GET_VAR;
	}
}
