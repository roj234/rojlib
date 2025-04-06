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
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
final class Trinary extends ExprNode {
	private ExprNode val, ok, fail;
	private IType type;
	private TypeCast.Cast cast;
	private byte boolHack;

	public Trinary(ExprNode val, ExprNode ok, ExprNode fail) {
		this.val = val;
		this.ok = ok;
		this.fail = fail;
	}

	@Override
	public String toString() { return val.toString()+" ? "+ok+" : "+fail; }

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) {
		// must before resolve
		if (val.hasFeature(ExprFeat.IMMEDIATE_CONSTANT))
			ctx.report(this, Kind.WARNING, "trinary.constant");

		val = val.resolve(ctx);
		cast = ctx.castTo(val.type(), Type.primitive(Type.BOOLEAN), 0);

		ok = ok.resolve(ctx);
		fail = fail.resolve(ctx);
		type = ctx.getCommonParent(ok.type(), fail.type());
		GlobalContext.debugLogger().info("common parent of "+ok.type()+" and "+fail.type()+" is "+type);

		if (cast.type >= 0 && val.isConstant())
			return (boolean) val.constVal() ? ok : fail;

		if (ok.equals(fail)) {
			ctx.report(this, Kind.WARNING, "trinary.constant");
		}

		if (ok.isConstant()&fail.isConstant()) {
			if (ok.constVal() instanceof CInt tv && fail.constVal() instanceof CInt fv) {
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
		if (boolHack != 0 && !(val instanceof Binary)) {
			mustBeStatement(noRet);
			GlobalContext.debugLogger().info("trinary.note.boolean_hack {}", this);

			val.write(cw, cast);
			int value = ((CInt) ok.constVal()).value;

			if (boolHack != 1) {
				cw.ldc(1);
				cw.one(Opcodes.IXOR);
			}

			if (value != 1) {
				cw.ldc(value);
				cw.one(Opcodes.IMUL);
			}
			return;
		}

		var vis = LocalContext.get().bp.vis();

		var end = new Label();
		var falsy = new Label();
		val.writeShortCircuit(cw, cast, false, falsy);
		vis.enter(null);
		//GenericSafe(using getCommonParent)
		ok.write(cw, noRet);
		vis.orElse();
		cw.jump(end);
		cw.label(falsy);
		fail.write(cw, noRet);
		cw.label(end);
		vis.exit();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Trinary tripleIf)) return false;
		return tripleIf.val.equals(val) && tripleIf.ok.equals(ok) && tripleIf.fail.equals(fail);
	}

	@Override
	public int hashCode() {
		int result = val.hashCode();
		result = 31 * result + ok.hashCode();
		result = 31 * result + fail.hashCode();
		return result;
	}
}