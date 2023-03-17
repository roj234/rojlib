package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2020/9/27 18:45
 */
public class VarGNode extends Node {
	Object name;

	public VarGNode(String name) {
		this.name = name;
	}

	@Override
	public Opcode getCode() {
		return Opcode.GET_VAR;
	}

	@Override
	public Node exec(Frame frame) {
		frame.push(frame.get(name.toString()).memory(-1));
		return next;
	}

	@Override
	Node replacement() {
		return name instanceof Node ? (Node) name : name instanceof Object[] ? (Node) ((Object[]) name)[1] : this;
	}

	@Override
	public String toString() {
		return "get " + name;
	}
}
