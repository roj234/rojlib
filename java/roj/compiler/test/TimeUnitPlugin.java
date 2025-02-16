package roj.compiler.test;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.LocalContext;
import roj.compiler.plugin.ExprApi;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;

import java.util.concurrent.TimeUnit;

/**
 * @author Roj234
 * @since 2024/12/4 13:19
 */
@LavaPlugin(name = "timeunitTest", desc = "ExprTerm测试插件")
public class TimeUnitPlugin extends ExprNode {
	private final TimeUnit unit;
	private ExprNode node;
	private TypeCast.Cast cast;

	public TimeUnitPlugin(ExprNode node, TimeUnit unit) {
		this.node = node;
		this.unit = unit;
	}

	public static void pluginInit(LavaApi api) {
		ExprApi rtApi = api.getExprApi();
		for (var unit : TimeUnit.values()) {
			rtApi.addExprTerminal(unit.name(), (lexer, node, lc) -> new TimeUnitPlugin(node, unit));
		}
	}

	@Override public String toString() { return node+" "+unit; }
	@Override public IType type() { return Type.primitive(Type.LONG); }

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		node = node.resolve(ctx);
		cast = ctx.castTo(node.type(), type(), 0);
		if (node.isConstant()) return Constant.valueOf(CEntry.valueOf(unit.toMillis(((CEntry) node.constVal()).asLong())));
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		node.write(cw, cast);
		cw.ldc(unit.toMillis(1));
		cw.one(Opcodes.LMUL);
	}
}