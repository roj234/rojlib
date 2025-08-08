package roj.compiler.plugins.moreop;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.JavaTokenizer;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.api.Types;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/11/11 22:34
 */
@CompilerPlugin(name = "moreop", desc = """
		More OP, More Power!
		操作符重载测试插件

		为Lava语言提供一些操作符语法糖""")
public final class MoreOpPlugin implements Compiler.ExprOp {
	private static final MethodNode STRING_CHARAT = new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "charAt", "()C");

	/**
	 * 启用之后 map[a] = b 返回的不是b，而是map.put(a,b)的返回值
	 */
	public static boolean UseOriginalPut;

	private final Type LIST_TYPE = Type.klass("java/util/List");
	private final Type MAP_TYPE = Type.klass("java/util/Map");
	public MoreOpPlugin() {}

	public void pluginInit(Compiler api) {
		api.onBinary(Type.klass("java/util/Collection"), "+=", Types.OBJECT_TYPE, new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, "java/util/Collection", "add", "(Ljava/lang/Object;)Z"), false);
		api.addOpHandler("[", this);
		api.onBinary(Type.klass("java/lang/String"), "*", Type.primitive(Type.INT), new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "repeat", "(I)Ljava/lang/String;"), false);
		api.onUnary("!", Type.klass("java/lang/String"), new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "isEmpty", "()Z"), 1);
	}

	@Override
	public Expr test(CompileContext ctx, Compiler.OperatorContext opctx, Expr left, Object right) {
		var sym = opctx.symbol();
		if (sym == JavaTokenizer.lBracket) {
			if (ctx.castTo(opctx.leftType(), LIST_TYPE, TypeCast.E_NEVER).type >= 0) {
				return new ListGet(left, (Expr) right);
			}
			if (ctx.castTo(opctx.leftType(), MAP_TYPE, TypeCast.E_NEVER).type >= 0) {
				return new MapGet(left, (Expr) right);
			}
			if (ctx.castTo(opctx.leftType(), Types.STRING_TYPE, TypeCast.E_NEVER).type >= 0) {
				return Invoke.virtualMethod(STRING_CHARAT, left, (Expr) right);
			}
		}
		return null;
	}
}
