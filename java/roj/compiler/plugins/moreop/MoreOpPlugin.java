package roj.compiler.plugins.moreop;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.api.Types;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.LocalContext;
import roj.compiler.plugin.ExprApi;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.compiler.resolve.TypeCast;

/**
 * @author Roj234
 * @since 2024/11/11 0011 22:34
 */
@LavaPlugin(name = "moreop", desc = "为Lava语言提供一些操作符语法糖")
public final class MoreOpPlugin implements ExprApi.ExprOp {
	private static final MethodNode STRING_CHARAT = new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "charAt", "()C");

	/**
	 * 启用之后 map[a] = b 返回的不是b，而是map.put(a,b)的返回值
	 */
	public static boolean UseOriginalPut;

	private final Type LIST_TYPE = Type.klass("java/util/List");
	private final Type MAP_TYPE = Type.klass("java/util/Map");
	public MoreOpPlugin() {}

	public void pluginInit(LavaApi ctx) {
		var expr = ctx.getExprApi();

		expr.onBinary(Type.klass("java/util/Collection"), "+=", Types.OBJECT_TYPE, new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, "java/util/Collection", "add", "(Ljava/lang/Object;)Z"), false);
		expr.addOpHandler("[", this);
		expr.onBinary(Type.klass("java/lang/String"), "*", Type.primitive(Type.INT), new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "repeat", "(I)Ljava/lang/String;"), false);
		expr.onUnary("!", Type.klass("java/lang/String"), new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "isEmpty", "()Z"), 1);
	}

	@Override
	public ExprNode test(LocalContext ctx, ExprApi.OperatorContext opctx, ExprNode left, Object right) {
		var sym = opctx.symbol();
		if (sym == JavaLexer.lBracket) {
			if (ctx.castTo(opctx.leftType(), LIST_TYPE, TypeCast.E_NEVER).type >= 0) {
				return new ListGet(left, (ExprNode) right);
			}
			if (ctx.castTo(opctx.leftType(), MAP_TYPE, TypeCast.E_NEVER).type >= 0) {
				return new MapGet(left, (ExprNode) right);
			}
			if (ctx.castTo(opctx.leftType(), Types.STRING_TYPE, TypeCast.E_NEVER).type >= 0) {
				return Invoke.virtualMethod(STRING_CHARAT, left, (ExprNode) right);
			}
		}
		return null;
	}
}
