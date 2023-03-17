package roj.kscript.asm;

import roj.asm.visitor.Label;

/**
 * @author Roj234
 * @since 2020/9/27 13:21
 */
public final class LabelNode extends Node {
	Label javaLabel;

	public LabelNode() {}

	public LabelNode(LabelNode node) {
		this.next = node.next;
	}

	@Override
	public Opcode getCode() {
		return Opcode.LABEL;
	}

	@Override
	public Node exec(Frame frame) {
		throw new IllegalStateException("This node should be executed!");
	}

}
