package roj.kscript.util;

import roj.kscript.asm.LabelNode;

/**
 * @author Roj234
 * @since 2020/10/17 1:41
 */
public final class LabelInfo {
	public final LabelNode head;
	public LabelNode onBreak, onContinue;

	public LabelInfo(LabelNode head, LabelNode breakTo, LabelNode continueTo) {
		this.head = head;
		this.onBreak = breakTo;
		this.onContinue = continueTo;
	}

	public LabelInfo(LabelNode head) {
		this.head = head;
	}

	@Override
	public String toString() {
		return "LabelInfo{" + "head=" + head + ", break=" + onBreak + ", continue=" + onContinue + '}';
	}
}
