package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.block.SwitchNode;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2024/6/9 0009 20:58
 */
final class Switch extends ExprNode {
	private final SwitchNode node;
	private IType type;
	public Switch(SwitchNode node) {this.node = node;}

	@Override
	public String toString() {return "<SwitchExpr> "+node;}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (type != null) return this;

		int coveredAll = 0;

		IType type = Helpers.maybeNull();
		for (SwitchNode.Case branch : node.branches) {
			ExprNode expr = branch.value;
			if (expr != null) {
				if (type == null) type = expr.type();
				else type = ctx.getCommonParent(type, expr.type());
			}

			// is default
			if (branch.labels == null) coveredAll = Integer.MIN_VALUE;
			else coveredAll += branch.labels.size();
		}
		this.type = type;

		for (SwitchNode.Case branch : node.branches) {
			ExprNode expr = branch.value;
			if (expr != null) {
				expr.writeDyn(branch.block, ctx.castTo(expr.type(), type, 0));
				branch.block.jump(node.breakTo);
				branch.value = null;
			}
		}

		type = node.sval.type();
		if (coveredAll > 0 && !type.isPrimitive()) {
			if (node.kind < 0) {
				// just class switch pattern
				// check sealed class
			} else {
				var info = ctx.getClassOrArray(type);
				if (info != null && (info.modifier&Opcodes.ACC_ENUM) != 0) {
					int count = 0;
					for (FieldNode field : info.fields) {
						if ((field.modifier&Opcodes.ACC_ENUM) != 0) {
							count++;
						}
					}
					if (count >= coveredAll) {
						if (count > coveredAll) {
							GlobalContext.debugLogger().warn("[WTF]switch的label引用的枚举字段超过了枚举自身的字段数量");
						} else {
							coveredAll = -1;
						}
					}
				}
			}
		}

		if (coveredAll >= 0) ctx.report(Kind.ERROR, "block.switch.exprMode.uncovered");

		return this;
	}

	@Override
	public IType type() {return type;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		LocalContext.get().bp.writeSwitch(node);
	}
}