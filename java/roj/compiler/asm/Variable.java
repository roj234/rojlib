package roj.compiler.asm;

import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.compiler.ast.stream.StreamChainImpl;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item implements Cloneable {
	public boolean isFinal, isVar, isParam, hasValue;
	public Object constantValue;
	public int startPos, endPos;
	public StreamChainImpl streamChain;

	public Object att;

	public Variable(String name, IType type) { super(name, type); }

	@Override public boolean equals(Object o) {return o == this;}
	@Override public int hashCode() {return System.identityHashCode(this);}
}