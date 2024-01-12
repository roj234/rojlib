package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.TypeCast;

import javax.tools.Diagnostic;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:06
 */
final class Trinary implements ExprNode {
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
	public ExprNode resolve(CompileContext ctx) {
		val = val.resolve(ctx);
		cast = ctx.castTo(val.type(), Type.std(Type.BOOLEAN), 0);

		ok = ok.resolve(ctx);
		fail = fail.resolve(ctx);
		type = ctx.getCommonParent(ok.type(), fail.type());
		ctx.report(Diagnostic.Kind.NOTE, "common parent of "+ok.type()+" and "+fail.type()+" is "+type);

		if (cast.type >= 0 && val.isConstant()) {
			ctx.report(Diagnostic.Kind.WARNING, "trinary.warn.always");
			return (boolean) val.constVal() ? ok : fail;
		}

		if (ok.equalTo(fail)) {
			ctx.report(Diagnostic.Kind.WARNING, "trinary.warn.always");
		}

		if (ok.isConstant()&fail.isConstant()) {
			if (ok.constVal() instanceof AnnValInt tv && fail.constVal() instanceof AnnValInt fv) {
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
		// TODO review possible side-effect
		if (boolHack != 0 && !(val instanceof Binary)) {
			mustBeStatement(noRet);
			cw.ctx1.report(Diagnostic.Kind.WARNING, "trinary.note.boolean_hack", this);

			val.writeDyn(cw, cast);
			int value = ((AnnValInt) ok.constVal()).value;

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
		Label falsy = new Label(), end = new Label();

		int i = cw.beginJumpOn(false, falsy);
		val.writeDyn(cw, cast);
		cw.endJumpOn(i);
		//GenericSafe(type use getCommonParent)
		ok.write(cw, noRet);
		cw.jump(end);
		cw.label(falsy);
		fail.write(cw, noRet);
		cw.label(end);
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof Trinary)) return false;
		Trinary tripleIf = (Trinary) o;
		return tripleIf.val.equalTo(val) && tripleIf.ok.equalTo(ok) && tripleIf.fail.equalTo(fail);
	}
}