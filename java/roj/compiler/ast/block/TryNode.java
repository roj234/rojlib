package roj.compiler.ast.block;

import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/30 0030 16:47
 */
public class TryNode {
	public List<ExprNode> closedAuto;

	public MethodWriter block;
	public Label returnHook;

	public List<TryCatchEntry> exceptions;
	public List<MethodWriter> handlers;

	public MethodWriter finalizer;
}