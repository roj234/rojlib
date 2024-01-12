package roj.compiler.asm;

import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.util.VarMapper;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item {
	public boolean constant;
	VarMapper.VarX att;
	int startPos, endPos;

	public Variable(String name) { super(name, Asterisk.anyType); }

	public Variable(String name, IType type) { super(name, type); }


	@Override
	public boolean equals(Object o) { return o == this; }

	@Override
	public int hashCode() { return System.identityHashCode(this); }
}