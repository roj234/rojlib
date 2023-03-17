package roj.kscript.asm;

import roj.kscript.api.IObject;

/**
 * @author Roj234
 * @since 2021/5/28 0:21
 */
public class MultiGetNode extends Node {
	String[] thus;

	protected MultiGetNode() {}

	@Override
	public Opcode getCode() {
		return Opcode.GET_OBJ;
	}

	@Override
	public Node exec(Frame frame) {
		IObject obj = frame.last().asObject();
		for (String s : thus) {
			obj = obj.get(s).asObject();
		}
		frame.setLast(obj);

		return next;
	}

}
