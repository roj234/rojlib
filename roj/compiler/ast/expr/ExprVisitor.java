package roj.compiler.ast.expr;

import roj.asm.type.Type;
import roj.lavac.expr.LoadNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/28 0028 21:18
 */
public interface ExprVisitor {
	void visitLocalVarConstantUpdate(String name, int count);
	void visitLocalVarDynamicUpdate(String name, ExprNode count);
	void visitArrayGet(LoadNode array, ExprNode index);
	void visitSizedArrayDefine(Type type, List<ExprNode> sizes);
	void visitUnSizedArrayDefine(Type type, List<ExprNode> expr);
	//void visitAssign
}
