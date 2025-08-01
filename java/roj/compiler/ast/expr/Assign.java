package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.Tokens;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CInt;

import static roj.compiler.Tokens.byId;

/**
 * AST - 赋值
 * @author Roj234
 * @since 2023/9/18 9:07
 */
final class Assign extends Expr {
	LeftValue left;
	Expr right;
	TypeCast.Cast cast;

	Assign(LeftValue left, Expr right) {
		this.left = left;
		this.right = right;
	}

	@Override public String toString() { return left+" = "+right; }
	@Override public IType type() { return left.type(); }

	public LeftValue getLeft() {return left;}
	public Expr getRight() {return right;}

	@Override
	public Expr resolve(CompileContext ctx) {
		Expr node = left.resolve(ctx);
		left = node.asLeftValue(ctx);
		if (left == null) return NaE.resolveFailed(this);

		Expr prev = right;
		if (prev instanceof Cast c) {
			int castType = c.cast.type;
			if ((castType == -1 || castType == -2) && !(left instanceof LocalVariable))
				ctx.report(this, Kind.INCOMPATIBLE, "assign.incompatible.redundantCast");
		}

		if (prev instanceof BinaryOp op) {
			right = op.resolveEx(ctx, true);
			if (right == NaE.resolveFailed(this)) return right;

			if (node.equals(op.left)) {
				// a = a + b
				// check binary_assign override
				var override = ctx.getOperatorOverride(node, op.right, op.operator - Tokens.binary_assign_delta);
				if (override != null) return override;
			}

			if (right == null) {
				ctx.report(this, Kind.ERROR, "op.notApplicable.binary", op.left.type(), op.right.type(), byId(op.operator));
				return NaE.resolveFailed(this);
			}
		} else {
			right = prev.resolve(ctx);
		}

		// 常量传播
		if (right.isConstant() && left instanceof LocalVariable lv) {
			ctx.assignVar(lv.v, right.constVal());
		}

		var lType = left.type();
		var rType = right.minType();
		var allow = right.isConstant() && lType.getActualType() == Type.CHAR;
		cast = ctx.castTo(rType, lType, allow ? TypeCast.E_EXPLICIT_CAST : 0);

		if (allow && cast.type == TypeCast.E_EXPLICIT_CAST) {
			var number = ((roj.config.data.CEntry)node.constVal()).asInt();
			if (number >= 0 && number <= 65535) return this;
			ctx.report(this, Kind.ERROR, "typeCast.error.-2", rType, lType);
		}
		//if (!isCastNeeded()) cast = null;

		return cast.type >= 0 ? this : NaE.resolveFailed(this);
	}

	static void incOrDec(LeftValue expr, MethodWriter cw, boolean noRet, boolean returnBefore, int amount) {
		var ctx = CompileContext.get();
		boolean isVariable = false;

		IType type = expr.type();
		int dataCap, primType = 0;
		if (type.isPrimitive()) {
			dataCap = TypeCast.getDataCap(type.getActualType());
		} else {
			primType = TypeCast.getWrappedPrimitive(type);
			if (primType == 0) throw new UnsupportedOperationException("incOrDec(expr = " + expr + ", cw = " + cw + ", noRet = " + noRet + ", returnBefore = " + returnBefore + ", amount = " + amount+") fail");
			dataCap = TypeCast.getDataCap(primType);
		}

		// == 4 (int) 防止byte自增导致溢出什么的...
		if (expr instanceof LocalVariable lv) {
			isVariable = true;

			if (dataCap == 4 && (short)amount == amount) {
				ctx.loadVar(lv.v);
				if (!noRet & returnBefore) cw.load(lv.v);
				cw.iinc(lv.v, amount);
				if (!noRet & !returnBefore) cw.load(lv.v);
				ctx.storeVar(lv.v);
				return;
			}
		}

		expr.preLoadStore(cw);

		int state = 0;
		if (!noRet & returnBefore) state = expr.copyValue(cw, type.rawType().length() - 1 != 0);
		if (primType != 0) ctx.castTo(type, Type.primitive(primType), 0).write(cw);

		int op;
		if (amount < 0 && amount != Integer.MIN_VALUE) {
			amount = -amount;
			op = Opcodes.ISUB-4;
		} else {
			op = Opcodes.IADD-4;
		}

		int type2 = Math.max(4, dataCap);
		switch (type2) {
			case 4: cw.ldc(amount); break;
			case 5: cw.ldc((long)amount); break;
			case 6: cw.ldc((float)amount); break;
			case 7: cw.ldc((double)amount); break;
		}
		cw.insn((byte) (op + type2));

		if (dataCap < 4 && primType == 0 && isVariable) cw.insn((byte) (Opcodes.I2B-1 + dataCap));
		else if (primType != 0) ctx.castTo(Type.primitive(primType), type, 0).write(cw);

		if (!noRet & !returnBefore) state = expr.copyValue(cw, type.rawType().length() - 1 != 0);
		expr.postStore(cw, state);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		// a = a + 1
		if (right instanceof BinaryOp op) {
			if (op.left.equals(left)) {
				if (sameVarShrink(cw, op, noRet, op.right)) return;
			} else if (op.left.isConstant() && op.right.equals(left)) {
				if (sameVarShrink(cw, op, noRet, op.left)) return;
			}

			if (isCastNeeded()) cast.write(cw);
		} else {
			left.preStore(cw);
			right.write(cw, isCastNeeded() ? cast : null);
		}

		int state = noRet ? 0 : left.copyValue(cw, left.type().rawType().length() - 1 != 0);
		left.postStore(cw, state);
	}

	private boolean isCastNeeded() { return (cast.type != -1 && cast.type != -2) || left.getClass() == LocalVariable.class; }

	private boolean sameVarShrink(MethodWriter cw, BinaryOp br, boolean noRet, Expr operand) {
		// to IINC if applicable
		block:
		if (left instanceof LocalVariable lv && TypeCast.getDataCap(br.left.type().getActualType()) == 4 && operand.isConstant()) {
			int value = ((CInt) operand.constVal()).value;

			switch (br.operator) {
				default: break block;
				case Tokens.add: break;
				case Tokens.sub: value = -value; break;
			}
			if ((short)value != value) break block;

			var ctx = CompileContext.get();

			ctx.loadVar(lv.v);
			cw.iinc(lv.v, value);
			if (!noRet) left.write(cw);
			ctx.storeVar(lv.v);
			return true;
		}

		left.preLoadStore(cw);
		if (operand == br.left) br.writeLeft(cw);
		else br.writeRight(cw);
		br.writeOperator(cw);

		return false;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Assign assign) || o.getClass() != getClass()) return false;
		return assign.left.equals(left) && assign.right.equals(right);
	}

	@Override
	public final int hashCode() {
		int result = left.hashCode();
		result = 31 * result + right.hashCode();
		result = 31 * result + getClass().hashCode();
		return result;
	}
}