package roj.kscript.asm;

import roj.kscript.type.KType;

/**
 * @author Roj234
 * @since 2020/9/27 23:59
 */
public final class LvtIncrNode extends Node {
	int ctxId, varId;
	final int val;

	public LvtIncrNode(int val) {
		this.val = val;
	}

	@Override
	public Opcode getCode() {
		return Opcode.INCREASE;
	}

	@Override
	public Node exec(Frame frame) {
		KType base = frame.parents[ctxId].getIdx(varId);
		if (base.isInt()) {
			base.setIntValue(base.asInt() + val);
		} else {
			base.setDoubleValue(base.asDouble() + val);
		}
		return next;
	}

	@Override
	public String toString() {
		return "(" + ctxId + ',' + varId + ") += " + val;
	}
}
