package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnValDouble;
import roj.asm.tree.anno.AnnValFloat;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.tree.anno.AnnValLong;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;

/**
 * @author Roj234
 * @since 2023/9/18 0018 7:53
 */
class UnaryPre extends UnaryPreNode {
	private final short op;
	ExprNode right;
	IType type;

	public UnaryPre(short op) { this.op = op; }

	@Override
	public String toString() { return byId(op)+' '+right; }

	@Override
	@SuppressWarnings("fallthrough")
	public ExprNode resolve(LocalContext ctx) {
		if (type != null) return this;

		right = right.resolve(ctx);
		type = right.type();

		boolean incrNode = op == inc || op == dec;

		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			ExprNode override = ctx.getOperatorOverride(right, null, op | LocalContext.UNARY_PRE);
			if (override != null) return override;

			iType = TypeCast.getWrappedPrimitive(type);
			if (iType == 0) {
				ctx.report(Kind.ERROR, "unary.error.notApplicable", byId(op), type);
				return NaE.RESOLVE_FAILED;
			}

			assert !right.isConstant() : "wrapped primitive is constant? weird";
			if (incrNode) ctx.report(Kind.SEVERE_WARNING, "unary.warn.wrapper", type, byId(op));
		}

		switch (iType) {
			case Type.VOID: ctx.report(Kind.ERROR, "unary.error.void"); return NaE.RESOLVE_FAILED;
			case Type.BOOLEAN:
				if (op != logic_not) {
					ctx.report(Kind.ERROR, "unary.error.notApplicable", byId(op), type);
					return NaE.RESOLVE_FAILED;
				}
				break;
			case Type.DOUBLE: case Type.FLOAT:
				if (op == inv || op == logic_not) {
					ctx.report(Kind.ERROR, "unary.error.notApplicable", byId(op), type);
					return NaE.RESOLVE_FAILED;
				}
				break;
			default: if (type.isPrimitive()) type = Type.std(Type.INT);
			case Type.LONG:
				if (op == logic_not) {
					ctx.report(Kind.ERROR, "unary.error.notApplicable:!", type);
					return NaE.RESOLVE_FAILED;
				}
		}

		if (incrNode && (!(right instanceof VarNode vn) || vn.isFinal())) {
			ctx.report(Kind.ERROR, "unary.error.final", right);
			return NaE.RESOLVE_FAILED;
		}

		if (!right.isConstant()) return this;

		switch (iType) {
			case Type.DOUBLE:
				if (op == sub) {
					AnnValDouble val = ((AnnValDouble) right.constVal());
					val.value = -val.value;
				}
				return right;
			case Type.FLOAT:
				if (op == sub) {
					AnnValFloat val = ((AnnValFloat) right.constVal());
					val.value = -val.value;
				}
				return right;
			case Type.BOOLEAN:
				return new Constant(type, !(boolean)right.constVal());
			case Type.LONG:
				AnnValLong lv = (AnnValLong)right.constVal();
				switch (op) {
					case sub: lv.value = -lv.value; break;
					case inv: lv.value ^= -1L; break;
				}
				return right;
			case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT:
				AnnValInt iv = (AnnValInt)right.constVal();
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
			case inc: case dec: Assign.incOrDec((VarNode) right, cw, noRet, false, op == inc ? 1 : -1); return;
		}

		mustBeStatement(noRet);
		right.write(cw);
		int iType = type.getActualType();
		if (iType == Type.CLASS) {
			iType = TypeCast.getWrappedPrimitive(type);
			assert iType != 0;
			// 手动拆箱，不通过TypeCast
			//noinspection MagicConstant
			Type primType = Type.std(iType);
			cw.invoke(Opcodes.INVOKEVIRTUAL, type.owner(), primType.toString().concat("Value"), "()".concat(primType.toDesc()));
		}

		switch (op) {
			case logic_not:
				// !a不就是这样么
				// 草，javac居然用跳转
				// 草，那我是不是也该用？参考ArrayDef中的留言
				cw.one(ICONST_1);
				cw.one(IXOR);
			break;
			case inv:
				// 我在期待什么... 甚至没有LCONST_M1
				if (iType == Type.LONG) {
					cw.ldc(-1L);
					cw.one(LXOR);
				} else {
					cw.one(ICONST_M1);
					cw.one(IXOR);
				}
			break;
			case sub:
				cw.one((byte) (INEG - 4 + Math.max(4, TypeCast.getDataCap(iType))));
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
		if (!(o instanceof UnaryPre left1)) return false;
		return left1.right.equals(right) && left1.op == op;
	}

	public String setRight(ExprNode right) {
		if (!(right instanceof VarNode)) {
			switch (op) {
				case inc: case dec: return "expr.unary.notVariable:".concat(byId(op));
			}
			if (right == null) return "expr.unary.noOperand:".concat(byId(op));
		}

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