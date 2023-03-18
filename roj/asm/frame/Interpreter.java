package roj.asm.frame;

import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.type.Type;
import roj.text.CharList;

/**
 * "Interpreter"
 *
 * @author Roj234
 * @version 1.1
 * @since 2021/6/18 9:51
 */
@Deprecated
public class Interpreter {
	public Interpreter() {}

	byte returnType;
	protected VarList stack, local;
	public int maxStackSize, maxLocalSize;

	CharList sb;

	public void init(MethodNode owner) {}
	final void pushRefArray(String name) {}
	protected static Var2 obj(String name) {
		return null;
	}
	Var2 pop() {
		return null;
	}
	final void checkStackTop(byte type) {}
	final Var2 pop(byte type) {
		return null;
	}
	final void pop(byte type, int count) {}
	public int visitNode(InsnNode node) {
		return 0;
	}
	final void checkReturn(char type) {}
	static Var2 fromType(Type type, CharList sb) {
		return null;
	}
}