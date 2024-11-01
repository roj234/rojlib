package roj.compiler.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.visitor.Label;
import roj.collect.SimpleList;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * 存放switch的数据
 * @author Roj234
 * @since 2024/6/16 0016 6:03
 */
public final class SwitchNode {
	/** 用于switch的表达式 */
	public final ExprNode sval;
	/** sval.constVal() 获取的常量 */
	@Nullable
	public final Object cst;
	/**
	 * SwitchMap目标类型
	 * -1 pattern switch （未实现）
	 * 0 int选择
	 * 1 传统String选择
	 * 2 传统Enum选择
	 * 3 identityHashCode的SwitchMap
	 * 4 默认hashCode的SwitchMap
	 */
	@Range(from = -1, to = 4)
	public final int kind;
	/** break; 跳到的位置 */
	public final Label breakTo;
	public final SimpleList<Case> branches;
	/** case null 所属的分支 注意: 它可能也在branches里 */
	@Nullable
	public final Case nullBranch;
	/** 是否含有default (对应Case的labels==null) */
	public final boolean defaultBranch;

	public SwitchNode(ExprNode sval, int kind, @Nullable Object cst, Label breakTo, SimpleList<Case> branches, @Nullable Case nullBranch, boolean defaultBranch) {
		this.sval = sval;
		this.kind = kind;
		this.cst = cst;
		this.breakTo = breakTo;
		this.branches = branches;
		this.nullBranch = nullBranch;
		this.defaultBranch = defaultBranch;
	}

	/**
	 * @author Roj234
	 * @since 2024/4/30 0030 16:48
	 */
	public static final class Case {
		/** 如果是Pattern switch那么非空，该分支的变量类型和名称 */
		public Variable variable;
		/** 如果是Type switch那么非空，该分支的常量或表达式 */
		public List<ExprNode> labels;

		/** 该分支的最后一个表达式，是switch表达式且能正常完成时非空，用来计算switch的返回类型 */
		@Nullable public ExprNode value;

		/** 代码 */
		@NotNull public MethodWriter block;

		// writeSwitch用到临时值
		public Label tmpLoc;
		/** case或default关键字结束时的行号 */
		public int lineNumber;

		public Case(List<ExprNode> labels) {this.labels = labels;}
		public Case(Variable v) {this.variable = v;}
	}
}