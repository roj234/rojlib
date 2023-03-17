package roj.kscript.asm;

import roj.kscript.type.KType;

/**
 * @author Roj234
 * @since 2020/9/27 23:59
 */
public final class IncrNode extends Node {
	Object name;
	final int val;

	public IncrNode(String name, int val) {
		this.name = name;
		this.val = val;
	}

	@Override
	public Opcode getCode() {
		return Opcode.INCREASE;
	}

	@Override
	public Node exec(Frame frame) {
		KType base = frame.get(name.toString());
		if (base.isInt()) {
			base.setIntValue(base.asInt() + val);
		} else {
			base.setDoubleValue(base.asDouble() + val);
		}
		return next;
	}

	@Override
	Node replacement() {
		return name instanceof Node ? (Node) name : name instanceof Object[] ? (Node) ((Object[]) name)[1] : this;
	}

	@Override
	public String toString() {
		return name + " += " + val;
	}
}
