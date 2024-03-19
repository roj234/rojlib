package roj.compiler.ast.block;

import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/30 0030 16:48
 */
public class SwitchNode {
	static class S {
		String targetClass;
		ExprNode targetValue; // resolve enum value here
		MethodWriter block;
	}

	public int expressionMode;
	public int flags; // FAST

	public ExprNode provider;
	public MethodWriter defaultBranch;
	public List<S> branches;
}