package roj.compiler.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.attr.LocalVariableTable;
import roj.asm.type.IType;
import roj.collect.IntervalPartition;

/**
 * @author Roj234
 * @since 2023/9/24 4:27
 */
public class Variable extends LocalVariableTable.Item implements IntervalPartition.Range {
	public Variable(String name, IType type) { super(name, type); }

	public boolean isFinal, hasValue;
	/**
	 * 不生成'未使用的变量'诊断
	 */
	public boolean forceUsed;
	/**
	 * 在下一次赋值时生成'该变量已隐式复制'诊断
	 * * 例如lambda或匿名内部类
	 */
	public boolean implicitCopied;

	/**
	 * 上次赋值过的常量值 | null
	 */
	@Nullable
	public Object value;

	/**
	 * 变量定义语句的文本位置，用于生成诊断
	 * * NestContext把这个设为0(magic number) 来阻止已知slot的立即序列化
	 */
	public int pos;

	// (WIP) 基于VisMap分区返回的占用状态：解决生命周期黑洞问题
	public IntervalPartition<?> complexStartEnd;

	public long startPos() { return start.getValue(); }
	public long endPos() { return end.getValue(); }

	@Override public boolean equals(Object o) {return o == this;}
	@Override public int hashCode() {return System.identityHashCode(this);}
}