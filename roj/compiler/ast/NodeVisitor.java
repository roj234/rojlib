package roj.compiler.ast;

import roj.asm.type.Type;
import roj.compiler.ast.block.VarDefNode;
import roj.compiler.ast.expr.ExprNode;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2023/11/4 0004 17:45
 */
public interface NodeVisitor {
	void enterBlock();
	void exitBlock();

	void forLoop();
	// can be many
	void forLoop_var(VarDefNode vari);
	void forLoop_cond(ExprNode cond);
	void forLoop_body();

	void whileLoop(ExprNode cond);

	void doWhileLoop();
	void doWhileLoop_cond(ExprNode cond);

	void nIf(ExprNode cond);
	void ifTrue();
	void elseIf();

	void enterTry();
	// can be many
	VarDefNode tryCatcher();
	void tryFinally();

	void enterSwitch(ExprNode cond);
	void switchBranch(ExprNode branch);
	void switchDefault();

	// TODO
	default void line(int line) {}
	void expr(ExprNode node);

	void nReturn(ExprNode expression);

	void label(String label);
	void nGoto(String label);
	void nBreak(@Nullable String label);
	void nContinue(@Nullable String label);

	void defineVariable(String name, Type type, int flags);
}
