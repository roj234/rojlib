package roj.compiler.asm;

import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.collect.RSegmentTree;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item implements RSegmentTree.Range {
	// refByNest仅用于诊断
	// isParam未使用
	// isVar用来计算共有类型
	public boolean isFinal, isVar, isParam, hasValue, refByNest;
	public Object constantValue;
	public int startPos, endPos;

	public Variable(String name, IType type) { super(name, type); }

	public long startPos() { return startPos; }
	public long endPos() { return endPos; }

	@Override public boolean equals(Object o) {return o == this;}
	@Override public int hashCode() {return System.identityHashCode(this);}
}