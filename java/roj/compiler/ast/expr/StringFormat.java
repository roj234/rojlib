package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.Tokenizer;

/**
 * @author Roj234
 * @since 2024/6/14 0014 8:05
 */
public class StringFormat extends ExprNode {
	private static final Type STRING_TYPE = new Type("java/lang/String");
	private final String template;
	private ExprNode prev;

	public StringFormat(VarNode prev, String template) {
		this.prev = prev;
		this.template = template;
	}

	@Override
	public String toString() {return prev+".\""+Tokenizer.addSlashes(template)+'"';}
	@Override
	public IType type() {return STRING_TYPE;}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		prev = prev.resolve(ctx);
		// 暂时还不懂怎么实现的，貌似是lambda
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		cw.ldc("**还没做好喵**");
	}

	@Override
	public void writeDyn(MethodWriter cw, TypeCast.@Nullable Cast cast) {
		super.writeDyn(cw, cast);
	}
}