package roj.kscript.asm;

import roj.kscript.type.KType;

/**
 * @author Roj234
 * @since 2020/9/27 18:45
 */
public class LoadDataNode extends Node {
	public KType data;
	private final KType curr;

	public LoadDataNode(KType data) {
		this.data = data;
		this.curr = data.copy();
	}

	@Override
	public Opcode getCode() {
		return Opcode.LOAD;
	}

	@Override
	public Node exec(Frame frame) {
		if (!data.equalsTo(curr)) {
			curr.copyFrom(data);
		}
		frame.push(curr);
		return next;
	}

	@Override
	public String toString() {
		return "Load(" + data + ')';
	}
}
