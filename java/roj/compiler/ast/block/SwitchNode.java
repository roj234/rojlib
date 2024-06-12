package roj.compiler.ast.block;

import org.jetbrains.annotations.Range;
import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/16 0016 6:03
 */
public final class SwitchNode {
	public final ExprNode sval;
	// SwitchMap kind
	// Pattern switch(-1) or Type switch(0..4)
	@Range(from = -1, to = 4)
	public final int kind;
	public final Object cst;
	public final Label breakTo;
	public final List<Case> branches;
	public final Case nullBranch;
	public final boolean defaultBranch;

	/**
	 *
	 */
	public SwitchNode(ExprNode sval, int kind, Object cst, Label breakTo, List<Case> branches, Case nullBranch,  boolean defaultBranch) {
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
	public static class Case {
		public Variable variable;
		public MethodWriter block;
		public List<Object> labels;
		public Label location;
		public int lineNumber;
		public ExprNode value;
		public boolean isContinuous;

		public Case(List<Object> labels) {this.labels = labels;}
	}
}