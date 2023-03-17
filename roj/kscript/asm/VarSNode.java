package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2020/9/27 18:45
 */
public final class VarSNode extends VarGNode {
	public VarSNode(String name) {
		super(name);
	}

	@Override
	public Opcode getCode() {
		return Opcode.PUT_VAR;
	}

	@Override
	public Node exec(Frame frame) {
		frame.put(name.toString(), frame.pop().memory(0));
		return next;
	}

	@Override
	public String toString() {
		return "lvt[" + name + "] = pop()";
	}
}
