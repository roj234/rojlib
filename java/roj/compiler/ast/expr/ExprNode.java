package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public abstract class ExprNode implements UnresolvedExprNode {
	public static final LongAdder ExprNodeNewCount = new LongAdder();

	public int wordStart, wordEnd;
	public int getWordStart() {return wordStart;}
	public int getWordEnd() {return wordEnd;}

	protected ExprNode() {
		ExprNodeNewCount.increment();
		var lc = LocalContext.get();
		if (lc != null) {
			wordStart = lc.lexer.current().pos();
			wordEnd = lc.lexer.index;
		}
	}
	protected ExprNode(int _noUpdate) {}

	public abstract String toString();

	public enum ExprKind {
		// this() or super()
		INVOKE_CONSTRUCTOR,
		// constant literal
		IMMEDIATE_CONSTANT,
		// literal xxx.class
		LDC_CLASS,
		// special kind for block passing parser
		CONSTANT_WRITABLE,
		ENUM_REFERENCE,
		//尾调用
		TAILREC
	}
	public boolean isKind(ExprKind kind) {return false;}
	public abstract IType type();
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return this; }

	@Override
	public boolean isConstant() { return UnresolvedExprNode.super.isConstant(); }
	@Override
	public Object constVal() { return UnresolvedExprNode.super.constVal(); }

	public abstract void write(MethodWriter cw, boolean noRet);
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		write(cw, false);
		if (cast != null) cast.write(cw);
	}
	protected static void mustBeStatement(boolean noRet) { if (noRet) LocalContext.get().report(Kind.ERROR, "expr.skipReturnValue"); }
}