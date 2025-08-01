package roj.compiler.test;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.Tokens;
import roj.compiler.api.Compiler;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.PrefixOperator;
import roj.config.Word;
import roj.io.FastFailException;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2024/2/20 1:28
 */
public class TestPlugin {
	public void pluginInit(Compiler api) {
		// <minecraft:stone>
		api.newStartOp("<", (ctx) -> {
			var wr = ctx.lexer;
			try {
				String nsKey = wr.except(Word.LITERAL).val();
				wr.except(Tokens.colon);
				String nsVal = wr.except(Word.LITERAL).val();
				wr.except(Tokens.gtr);

				MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "roj/compiler/test/CandyTestPlugin$Item", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
				return Invoke.constructor(mn, Expr.valueOf(nsKey), Expr.valueOf(nsVal));
			} catch (Exception e) {
				Helpers.athrow(e);
				return null;
			}
		});

		// <minecraft:stone> * 5
		MethodNode mn = new MethodNode(Opcodes.ACC_STATIC, "roj/compiler/test/CandyTestPlugin$Item", "stack", "(Lroj/compiler/test/CandyTestPlugin$Item;I)Lroj/compiler/test/CandyTestPlugin$ItemStack;");
		api.onBinary(Type.klass("roj/compiler/test/CandyTestPlugin$Item"), "*", Type.primitive(Type.INT), mn, true);

		api.newUnaryOp("__TypeOf", (ctx, node) -> new PrefixOperator() {
			Expr node;
			@Override public String setRight(Expr node) {this.node = node;return null;}
			@Override public String toString() {return null;}
			@Override public IType type() {return null;}
			@Override public void write(MethodWriter cw, boolean noRet) {
				throw new FastFailException("[\n  表达式="+node+"\n  解析="+(node = node.resolve(CompileContext.get()))+"\n  返回类型="+node.type()+"\n]");
			}
		});
	}
}