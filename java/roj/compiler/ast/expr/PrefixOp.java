package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CDouble;
import roj.config.data.CFloat;
import roj.config.data.CInt;
import roj.config.data.CLong;

import static roj.asm.Opcodes.*;
import static roj.compiler.Tokens.*;

/**
 * @author Roj234
 * @since 2023/9/18 7:53
 */
class PrefixOp extends PrefixOperator {
	private final short op;
	Expr right;
	IType type;

	public PrefixOp(short op) { this.op = op; }

	@Override
	public String toString() { return byId(op)+' '+right; }

	@Override
	@SuppressWarnings("fallthrough")
	public Expr resolve(LocalContext ctx) {
		if (type != null) return this;

		right = right.resolve(ctx);
		type = right.type();

		boolean incrNode = op == inc || op == dec;

		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			Expr override = ctx.getOperatorOverride(right, null, op | LocalContext.UNARY_PRE);
			if (override != null) return override;

			iType = TypeCast.getWrappedPrimitive(type);
			if (iType == 0) {
				ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
				return NaE.RESOLVE_FAILED;
			}

			assert !right.isConstant() : "wrapped primitive is constant? weird";
			if (incrNode) ctx.report(this, Kind.SEVERE_WARNING, "unary.warn.wrapper", type, byId(op));
		}

		switch (iType) {
			case Type.VOID: ctx.report(this, Kind.ERROR, "var.voidType", right); return NaE.RESOLVE_FAILED;
			case Type.BOOLEAN:
				if (op != logic_not) {
					ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
					return NaE.RESOLVE_FAILED;
				}
				break;
			case Type.DOUBLE: case Type.FLOAT:
				if (op == inv || op == logic_not) {
					ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
					return NaE.RESOLVE_FAILED;
				}
				break;
			default: if (type.isPrimitive()) type = Type.primitive(Type.INT);
			case Type.LONG:
				if (op == logic_not) {
					ctx.report(this, Kind.ERROR, "op.notApplicable.unary", "!", type);
					return NaE.RESOLVE_FAILED;
				}
		}

		if (incrNode && (right = right.asLeftValue(ctx)) == null) return NaE.RESOLVE_FAILED;

		if (!right.isConstant()) return this;

		switch (iType) {
			case Type.DOUBLE:
				if (op == sub) {
					var val = ((CDouble) right.constVal());
					val.value = -val.value;
				}
				return right;
			case Type.FLOAT:
				if (op == sub) {
					var val = ((CFloat) right.constVal());
					val.value = -val.value;
				}
				return right;
			case Type.BOOLEAN:
				return constant(type, !(boolean)right.constVal());
			case Type.LONG:
				var lv = (CLong)right.constVal();
				switch (op) {
					case sub: lv.value = -lv.value; break;
					case inv: lv.value ^= -1L; break;
				}
				return right;
			case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT:
				var iv = (CInt)right.constVal();
				switch (op) {
					case sub: iv.value = -iv.value; break;
					case inv: iv.value ^= -1; break;
				}
				return right;
		}
		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		switch (op) {
			case inc: case dec: Assign.incOrDec((LeftValue) right, cw, noRet, false, op == inc ? 1 : -1); return;
		}

		mustBeStatement(noRet);
		right.write(cw);
		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			iType = TypeCast.getWrappedPrimitive(type);
			assert iType != 0;
			// 手动拆箱，不通过TypeCast
			//noinspection MagicConstant
			Type primType = Type.primitive(iType);
			cw.invoke(Opcodes.INVOKEVIRTUAL, type.owner(), primType.toString().concat("Value"), "()".concat(primType.toDesc()));
		}

		switch (op) {
			case logic_not:
				// !a不就是这样么
				// 草，javac居然用跳转
				// 草，那我是不是也该用？参考ArrayDef中的留言
				cw.insn(ICONST_1);
				cw.insn(IXOR);
			break;
			case inv:
				// 我在期待什么... 甚至没有LCONST_M1
				if (iType == Type.LONG) {
					cw.ldc(-1L);
					cw.insn(LXOR);
				} else {
					cw.insn(ICONST_M1);
					cw.insn(IXOR);
				}
			break;
			case sub:
				cw.insn((byte) (INEG - 4 + Math.max(4, TypeCast.getDataCap(iType))));
			break;
		}
	}

	@Override
	public void writeShortCircuit(MethodWriter cw, TypeCast.@Nullable Cast cast, boolean ifThen, @NotNull Label label) {
		if (op != logic_not) super.writeShortCircuit(cw, cast, ifThen, label);
		else right.writeShortCircuit(cw, cast, !ifThen, label);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PrefixOp left1)) return false;
		return left1.right.equals(right) && left1.op == op;
	}

	public String setRight(Expr right) {
		if (right == null) return "noExpression";
		this.right = right;
		return null;
	}

	@Override
	public int hashCode() {
		int result = op;
		result = 31 * result + right.hashCode();
		return result;
	}
}