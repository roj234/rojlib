package roj.compiler.asm;

import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.compiler.ast.stream.StreamChainImpl;
import roj.util.VarMapper;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item implements Cloneable {
	public boolean isFinal, hasValue, isDynamic;
	public Object constantValue;
	public int startPos, endPos;
	public int useCount;
	public StreamChainImpl streamChain;

	VarMapper.VarX att;

	public Variable(String name, IType type) { super(name, type); }

	// 变量是否被定义的检测
	public Variable fork() {
		try {
			return (Variable) clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
	public void join(Variable v) {
		if (!v.hasValue) hasValue = false;
		if (v.constantValue != constantValue) constantValue = null;
		endPos = Math.max(endPos, v.endPos);
		useCount += v.useCount;
		if (v.streamChain != streamChain) throw new IllegalArgumentException("stream chain merge failed");
	}

	@Override
	public boolean equals(Object o) { return o == this; }

	@Override
	public int hashCode() { return System.identityHashCode(this); }
}