package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.IType;
import roj.collect.HashSet;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.SwitchNode;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/9 20:58
 */
final class Switch extends Expr {
	private final SwitchNode node;
	private IType type;
	public Switch(SwitchNode node) {this.node = node;}

	@Override
	public String toString() {return "switch "+node;}

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if (type != null) return this;

		int coveredAll = node.defaultBranch ? Integer.MIN_VALUE : 0;

		IType type = null;
		for (var branch : node.branches) {
			Expr expr = branch.value;
			if (expr != null) {
				if (type == null) type = expr.type();
				else type = ctx.getCommonParent(type, expr.type());
			}

			if (branch.labels != null) coveredAll += branch.labels.size();
		}
		this.type = type;

		type = node.sval.type();
		if (coveredAll >= 0 && !type.isPrimitive()) {
			var info = ctx.resolve(type);
			if (info == null) throw new AssertionError("parent node "+node.sval+" must return NaE.RESOLVE_FAILED if resolution failed");

			if (node.kind < 0) {
				// abstract sealed
				if ((info.modifier&Opcodes.ACC_ABSTRACT) != 0 && info.getAttribute("PermittedSubclasses") != null) {
					HashSet<String> patternType = new HashSet<>();
					for (var branch : node.branches) {
						patternType.add(branch.variable.type.owner());
					}

					if (isFullySealed(ctx.compiler, info, patternType)) {
						coveredAll = -1;
						node.shouldNotReachDefault(ctx);
					}
				} else if (type.genericType() == IType.OR_TYPE) {
					HashSet<IType> patternType = new HashSet<>();
					patternType.addAll(((Asterisk) type).getTraits());
					for (var branch : node.branches) {
						if (patternType.isEmpty()) LavaCompiler.debugLogger().warn("ExprSwitch FailFast");

						for (var itr = patternType.iterator(); itr.hasNext(); ) {
							var cast = ctx.castTo(itr.next(), branch.variable.type, -8);
							if (cast.type >= 0) itr.remove();
						}
					}

					if (patternType.isEmpty()) {
						coveredAll = -1;
						node.shouldNotReachDefault(ctx);
					}
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
							LavaCompiler.debugLogger().warn("ExprSwitch label引用的枚举字段超过了枚举自身的字段数量");
						} else {
							coveredAll = -1;
						}
					}
				}
			}
		}

		if (coveredAll >= 0) ctx.report(this, Kind.ERROR, "block.switch.exprMode.uncovered");

		return this;
	}

	private boolean isFullySealed(LavaCompiler ctx, ClassNode info, HashSet<String> patterns) {
		// 如果当前类是目标，那么不再需要检测子类
		if (patterns.remove(info.name())) return true;

		var subclasses = info.getAttribute(info.cp, Attribute.PermittedSubclasses);
		if ((info.modifier&Opcodes.ACC_ABSTRACT) != 0 && subclasses != null) {
			List<String> value = subclasses.value;
			for (int i = 0; i < value.size(); i++) {
				var child = ctx.resolve(value.get(i));
				if (child == null || !isFullySealed(ctx, child, patterns)) return false;
			}

			// 所有子类都覆盖了，那么是允许的
			return true;
		}

		// non-sealed或final
		return false;
	}

	@Override
	public IType type() {return type;}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);

		var ctx = CompileContext.get();

		var type = this.type;
		if (cast.getType1() != null) type = cast.getType1();

		for (var branch : node.branches) {
			Expr expr = branch.value;
			if (expr != null) {
				expr.write(branch.block, ctx.castTo(expr.type(), type, 0));
				branch.block.jump(node.breakTo);
				branch.value = null;
			}
		}

		ctx.bp.writeSwitch(node);
	}
}