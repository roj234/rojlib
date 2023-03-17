package roj.lavac.block;

import roj.asm.tree.insn.LabelInsnNode;

/**
 * @author Roj234
 * @since 2022/10/22 15:01
 */
public final class LabelInfo {
	public final LabelInsnNode head;
	public LabelInsnNode onBreak, onContinue;

	public LabelInfo(LabelInsnNode head, LabelInsnNode breakTo, LabelInsnNode continueTo) {
		this.head = head;
		this.onBreak = breakTo;
		this.onContinue = continueTo;
	}

	public LabelInfo(LabelInsnNode head) {
		this.head = head;
	}

	@Override
	public String toString() {
		return "LabelInfo{" + "head=" + head + ", break=" + onBreak + ", continue=" + onContinue + '}';
	}
}
