package roj.compiler.plugins.asm;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.config.Tokenizer;

/**
 * @author Roj234
 * @since 2024/2/6 0006 12:47
 */
final class AsmExpr extends ExprNode {
	private final String asm;
	public AsmExpr(String str) {this.asm = str;}

	@Override
	public String toString() {return "__asm("+Tokenizer.addSlashes(asm)+")";}
	@Override
	public boolean isConstant() {return true;}
	@Override
	public Object constVal() {return false;}
	@Override
	public boolean isKind(ExprKind kind) {return kind == ExprKind.CONSTANT_WRITABLE;}

	@Override
	public IType type() {return Type.std(Type.BOOLEAN);}
	@Override
	public void write(MethodWriter cw, boolean noRet) {
		if (!noRet) throw new IllegalStateException();

		MethodWriter fork = cw.fork();
		new AsmLangParser().parse(asm, fork);
		fork.writeTo(cw);
	}
}