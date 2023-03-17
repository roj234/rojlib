package roj.kscript.asm;

import roj.kscript.parser.JSLexer;
import roj.kscript.type.KBool;

/**
 * @author Roj234
 * @since 2020/9/27 18:50
 */
public final class IfLoadNode extends Node {
	final byte type;

	public IfLoadNode(short type) {
		if (type == IfNode.TRUE - 53) throw new IllegalArgumentException("NO IS_TRUE available");
		this.type = (byte) (type - 53);
	}

	@Override
	public Opcode getCode() {
		return Opcode.IF_LOAD;
	}

	@Override
	public Node exec(Frame frame) {
		frame.push(KBool.valueOf(IfNode.calcIf(frame, type)));
		return next;
	}

	@Override
	public String toString() {
		return "If_Load " + type + JSLexer.byId((short) (type + 53));
	}
}
