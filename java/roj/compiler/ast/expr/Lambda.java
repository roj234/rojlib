package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;

/**
 * Lambda要么是方法参数，要么是Assign的目标
 * @author Roj234
 * @since 2024/1/23 0023 11:32
 */
public class Lambda extends ExprNode {
	private List<String> args;
	private ExprNode expr;

	// args[0] -> expr
	public Lambda(List<String> args, ExprNode expr) {
		this.args = args;
		this.expr = expr;
	}
	// args -> {...}
	public Lambda(MethodNode mn, ParseTask task) {

	}
	// parent::methodRef
	public Lambda(ExprNode parent, String methodRef) {
		this.expr = parent;
		this.args = Collections.singletonList(methodRef);
	}

	public int argCount() {
		return args.size();
	}

	@Override
	public String toString() { return (args.size() == 1 ? args.get(0) : "("+TextUtil.join(args, ", ")+")") + " -> " + expr; }

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		return super.resolve(ctx);
	}

	@Override
	public IType type() {return Asterisk.anyType;}
	@Override
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("lambda.error.untyped");}

	@Override
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		super.writeDyn(cw, cast);
	}

	// TODO
	@Override
	public boolean equals(Object o) {
		return false;
	}
}