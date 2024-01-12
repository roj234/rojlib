package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.TypeCast;
import roj.config.word.NotStatementException;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/23 0023 11:32
 */
public class Lambda implements ExprNode {
	private List<String> args;
	private ExprNode expr;

	// args[0] -> expr
	public Lambda(List<String> args, ExprNode expr) {
		this.args = args;
		this.expr = expr;
	}
	// args -> {...}
	public Lambda(List<String> args, MethodNode methodNode) {

	}
	// parent::methodRef
	public Lambda(ExprNode parent, String methodRef) {
		this.expr = parent;
		this.args = Collections.singletonList(methodRef);
	}

	@Override
	public String toString() { return args + " -> " + expr.toString(); }

	@Override
	public IType type() {
		return Asterisk.anyType;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {

	}

	@Override
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		ExprNode.super.writeDyn(cw, cast);
	}

	@Override
	public boolean equalTo(Object o) {
		return false;
	}
}