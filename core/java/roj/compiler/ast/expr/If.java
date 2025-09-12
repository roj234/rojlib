package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.node.IntValue;

/**
 * AST - 三元(条件)运算符和if表达式
 * @author Roj234
 * @since 2023/9/18 9:06
 */
final class If extends Expr {
	private Expr condition, trueBranch, falseBranch;
	private IType type;
	private TypeCast.Cast cast;
	private byte boolHack;

	public If(Expr condition, Expr trueBranch, Expr falseBranch) {
		this.condition = condition;
		this.trueBranch = trueBranch;
		this.falseBranch = falseBranch;
	}

	@Override
	public String toString() { return condition+" ? "+trueBranch+" : "+falseBranch; }

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) {
		// must before resolve
		if (condition.hasFeature(Feature.IMMEDIATE_CONSTANT))
			ctx.report(this, Kind.WARNING, "if.constant");

		condition = condition.resolve(ctx);
		cast = ctx.castTo(condition.type(), Type.primitive(Type.BOOLEAN), 0);

		trueBranch = trueBranch.resolve(ctx);
		falseBranch = falseBranch.resolve(ctx);
		type = ctx.getCommonParent(trueBranch.type(), falseBranch.type());

		if (cast.type >= 0 && condition.isConstant())
			return (boolean) condition.constVal() ? trueBranch : falseBranch;

		if (trueBranch.equals(falseBranch)) {
			ctx.report(this, Kind.WARNING, "if.constant");
		}

		if (trueBranch.isConstant()& falseBranch.isConstant()) {
			if (trueBranch.constVal() instanceof IntValue tv && falseBranch.constVal() instanceof IntValue fv) {
				if (fv.value == 0) {
					// if not 1 => IMUL
					// else => NOP
					boolHack = 1;
				} else if (tv.value == 0) {
					// IXOR
					// if not 1 => IMUL
					// else => NOP
					boolHack = 2;
				}
			}
		}
		return this;
	}

	@Override
	public IType type() { return type; }

	// 通过这种处理(不检查noRet)，现在可以直接使用 ? : 而不是必须在有返回值的环境了
	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		if (boolHack != 0 && !(condition instanceof BinaryOp)) {
			mustBeStatement(cast);
			LavaCompiler.debugLogger().info("trinary.note.boolean_hack {}", this);

			condition.write(cw, this.cast);
			int value = ((IntValue) trueBranch.constVal()).value;

			if (boolHack != 1) {
				cw.ldc(1);
				cw.insn(Opcodes.IXOR);
			}

			if (value != 1) {
				cw.ldc(value);
				cw.insn(Opcodes.IMUL);
			}
			return;
		}

		var vis = CompileContext.get().bp.vis();
		var end = new Label();
		var falsy = new Label();

		condition.writeShortCircuit(cw, this.cast, false, falsy);
		vis.enter(null);

		cast = noRet(cast);

		//GenericSafe(using getCommonParent)
		trueBranch.write(cw, cast);
		vis.orElse();
		cw.jump(end);

		cw.label(falsy);
		falseBranch.write(cw, cast);

		cw.label(end);
		vis.exit();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof If tripleIf)) return false;
		return tripleIf.condition.equals(condition) && tripleIf.trueBranch.equals(trueBranch) && tripleIf.falseBranch.equals(falseBranch);
	}

	@Override
	public int hashCode() {
		int result = condition.hashCode();
		result = 31 * result + trueBranch.hashCode();
		result = 31 * result + falseBranch.hashCode();
		return result;
	}
}