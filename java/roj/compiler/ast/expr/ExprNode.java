package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.WillChange;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.Set;
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

	// Not used, 高级优化使用，例如将 t = num * i 转换为 t = 0, loop<t += num>，以及代码提升
	public boolean hasNoSideEffect(
		Set<Variable> variablesShouldNotRefersTo,
		Set<Variable> variablesMayRefersTo,
		@WillChange
		Set<Variable> variablesReferredTo
								  ) {return false;}

	public enum ExprFeat {
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
		TAILREC,
		STATIC_BEGIN
	}
	public boolean hasFeature(ExprFeat kind) {return false;}
	public abstract IType type();
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return this; }

	@Override public boolean isConstant() {return UnresolvedExprNode.super.isConstant();}
	@Override public Object constVal() {return UnresolvedExprNode.super.constVal();}

	public final void write(MethodWriter cw) {write(cw, false);}
	public abstract void write(MethodWriter cw, boolean noRet);
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		write(cw, false);
		if (returnType != null) returnType.write(cw);
	}
	public void writeShortCircuit(MethodWriter cw, @Nullable TypeCast.Cast cast,
								   boolean ifThen, @NotNull Label label) {
		write(cw, cast);
		cw.jump(ifThen ? Opcodes.IFNE/*true*/ : Opcodes.IFEQ/*false*/, label);
	}

	protected static void mustBeStatement(boolean noRet) { if (noRet) LocalContext.get().report(Kind.ERROR, "expr.skipReturnValue"); }
}