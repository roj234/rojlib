package roj.lavac.asm;

import roj.asm.frame.Var2;
import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.type.Type;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item {
	public boolean constant;
	//VarX att;

	public Variable(String name) {
		super("", null);
	}

	public Variable(String name, Type type) {
		super(name, type);
	}

	public Var2 curType;
	int startPos, endPos;

	@Override
	public boolean equals(Object o) {
		return o == this;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}
}
