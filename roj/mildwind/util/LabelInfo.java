package roj.mildwind.util;

import roj.asm.visitor.Label;

/**
 * @author Roj234
 * @since 2020/10/17 1:41
 */
public final class LabelInfo {
	public final Label head;
	public Label onBreak, onContinue;

	public LabelInfo(Label head, Label breakTo, Label continueTo) {
		this.head = head;
		this.onBreak = breakTo;
		this.onContinue = continueTo;
	}

	public LabelInfo(Label head) {
		this.head = head;
	}

	@Override
	public String toString() {
		return "LabelInfo{" + "head=" + head + ", break=" + onBreak + ", continue=" + onContinue + '}';
	}
}
