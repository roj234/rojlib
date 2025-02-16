package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.IType;
import roj.collect.MyHashSet;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.SwitchNode;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.util.Helpers;

import java.util.List;

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

		type = node.sval.type();
		if (coveredAll > 0 && !type.isPrimitive()) {
			var info = ctx.getClassOrArray(type);
			if (info == null) throw new IllegalStateException("parent node "+node.sval+" did not return NaE.RESOLVE_FAILED for null type");

			if (node.kind < 0) {
				// abstract sealed
				if ((info.modifier&Opcodes.ACC_ABSTRACT) != 0 && info.attrByName("PermittedSubclasses") != null) {
					MyHashSet<String> patternType = new MyHashSet<>();
					for (SwitchNode.Case branch : node.branches) {
						patternType.add(branch.variable.type.owner());
					}
					coveredAll = iterSealed(ctx.classes, info, patternType) ? -1 : 0;
				}
			} else {
				if ((info.modifier&Opcodes.ACC_ENUM) != 0) {
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

	private boolean iterSealed(GlobalContext ctx, ClassNode info, MyHashSet<String> patterns) {
		var s = info.parsedAttr(info.cp, Attribute.PermittedSubclasses);
		if (s != null) {
			List<String> value = s.value;
			for (int i = 0; i < value.size(); i++) {
				String type = value.get(i);
				if (!patterns.remove(type)) return false;
				info = ctx.getClassInfo(type);
				if (info == null || !iterSealed(ctx, info, patterns)) return false;
			}
		}
		return true;
	}

	@Override
	public IType type() {return type;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		write(cw, null);
	}

	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		var ctx = LocalContext.get();

		var type = this.type;
		if (returnType != null && returnType.getType1() != null) type = returnType.getType1();

		for (SwitchNode.Case branch : node.branches) {
			ExprNode expr = branch.value;
			if (expr != null) {
				expr.write(branch.block, ctx.castTo(expr.type(), type, 0));
				branch.block.jump(node.breakTo);
				branch.value = null;
			}
		}

		ctx.bp.writeSwitch(node);
	}
}