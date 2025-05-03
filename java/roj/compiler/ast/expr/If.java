package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CInt;

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
	public Expr resolve(LocalContext ctx) {
		// must before resolve
		if (condition.hasFeature(Feature.IMMEDIATE_CONSTANT))
			ctx.report(this, Kind.WARNING, "trinary.constant");

		condition = condition.resolve(ctx);
		cast = ctx.castTo(condition.type(), Type.primitive(Type.BOOLEAN), 0);

		trueBranch = trueBranch.resolve(ctx);
		falseBranch = falseBranch.resolve(ctx);
		type = ctx.getCommonParent(trueBranch.type(), falseBranch.type());

		if (cast.type >= 0 && condition.isConstant())
			return (boolean) condition.constVal() ? trueBranch : falseBranch;

		if (trueBranch.equals(falseBranch)) {
			ctx.report(this, Kind.WARNING, "trinary.constant");
		}

		if (trueBranch.isConstant()& falseBranch.isConstant()) {
			if (trueBranch.constVal() instanceof CInt tv && falseBranch.constVal() instanceof CInt fv) {
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
	public void write(MethodWriter cw, boolean noRet) {
		if (boolHack != 0 && !(condition instanceof BinaryOp)) {
			mustBeStatement(noRet);
			GlobalContext.debugLogger().info("trinary.note.boolean_hack {}", this);

			condition.write(cw, cast);
			int value = ((CInt) trueBranch.constVal()).value;

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

		var vis = LocalContext.get().bp.vis();
		var end = new Label();
		var falsy = new Label();

		condition.writeShortCircuit(cw, cast, false, falsy);
		vis.enter(null);

		//GenericSafe(using getCommonParent)
		trueBranch.write(cw, noRet);
		vis.orElse();
		cw.jump(end);

		cw.label(falsy);
		falseBranch.write(cw, noRet);

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