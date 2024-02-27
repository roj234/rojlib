package roj.compiler.ast;

import roj.asm.visitor.Label;

/**
 * @author Roj234
 * @since 2022/10/22 15:01
 */
public final class LabelNode {
	public final Label head;
	public Label onBreak, onContinue;

	public LabelNode(Label head) {this.head = head;}
	public LabelNode(Label head, Label breakTo, Label continueTo) {
		this.head = head;
		this.onBreak = breakTo;
		this.onContinue = continueTo;
	}

	@Override
	public String toString() {return "LabelInfo{"+"head="+head+", break="+onBreak+", continue="+onContinue+'}';}
}