package roj.compiler.asm;

import roj.asm.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.collect.RSegmentTree;

/**
 * @author Roj234
 * @since 2023/9/24 0024 4:27
 */
public class Variable extends LocalVariableTable.Item implements RSegmentTree.Range {
	// refByNest仅用于诊断
	public boolean isFinal, ignoreUnusedCheck, hasValue, refByNest;

	// TODO assigned from X -> 在X下一次赋值前可以使用当前变量替代
	public Object constantValue;

	public int pos;
	// (unused) 基于VisMap分区返回的占用状态：解决生命周期黑洞问题
	public RSegmentTree<?> complexStartEnd;

	public Variable(String name, IType type) { super(name, type); }

	public long startPos() { return start.getValue(); }
	public long endPos() { return end.getValue(); }

	@Override public boolean equals(Object o) {return o == this;}
	@Override public int hashCode() {return System.identityHashCode(this);}
}