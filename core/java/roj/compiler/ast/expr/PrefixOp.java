package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.node.DoubleValue;
import roj.config.node.FloatValue;
import roj.config.node.IntValue;
import roj.config.node.LongValue;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaTokenizer.*;

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
	public Expr resolve(CompileContext ctx) {
		if (type != null) return this;

		right = right.resolve(ctx);
		type = right.type();

		boolean mutate = op == inc || op == dec;

		int actualType = type.getActualType();
		if (actualType == Type.CLASS) {
			actualType = TypeCast.getWrappedPrimitive(type);
			if (actualType == 0) return notApplicable(ctx);
			if (mutate) ctx.report(this, Kind.SEVERE_WARNING, "op.wrapper", type, byId(op));
		}

		switch (actualType) {
			case Type.BOOLEAN:
				if (op != logic_not) return notApplicable(ctx);
			break;
			case Type.DOUBLE, Type.FLOAT:
				if (op == inv || op == logic_not) return notApplicable(ctx);
			break;

			default: type = Type.INT_TYPE;
			case Type.CLASS, Type.LONG:
				if (op != logic_not) break;
			// fallthrough
			case Type.VOID: return notApplicable(ctx);
		}

		if (mutate && (right = right.asLeftValue(ctx)) == null) return NaE.resolveFailed(this);

		if (!right.isConstant()) return this;

		return switch (Type.getSort(actualType)) {
			default -> this;
			case Type.SORT_BOOLEAN -> constant(type, !(boolean) right.constVal());
			case Type.SORT_BYTE, Type.SORT_CHAR, Type.SORT_SHORT, Type.SORT_INT -> {
				var x = (IntValue) right.constVal();
				switch (op) {
					case sub -> x.value = -x.value;
					case inv -> x.value ^= -1;
				}
				yield right;
			}
			case Type.SORT_LONG -> {
				var x = (LongValue) right.constVal();
				switch (op) {
					case sub -> x.value = -x.value;
					case inv -> x.value ^= -1L;
				}
				yield right;
			}
			case Type.SORT_FLOAT -> {
				if (op == sub) {
					var val = (FloatValue) right.constVal();
					val.value = -val.value;
				}
				yield right;
			}
			case Type.SORT_DOUBLE -> {
				if (op == sub) {
					var val = (DoubleValue) right.constVal();
					val.value = -val.value;
				}
				yield right;
			}
		};
	}

	@NotNull
	private Expr notApplicable(CompileContext ctx) {
		Expr override = ctx.getOperatorOverride(right, null, op | CompileContext.UNARY_PRE);
		if (override != null) return override;

		ctx.report(this, Kind.ERROR, "op.notApplicable.unary", byId(op), type);
		return NaE.resolveFailed(this);
	}

	@Override
	public IType type() { return type; }

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		// inc or dec
		if (op <= dec) {
			Assign.incOrDec((LeftValue) right, cw, cast == NORET, false, op == inc ? 1 : -1);
			return;
		}

		mustBeStatement(cast);
		right.write(cw);

		int actualType = type.getActualType();
		if (actualType == Type.CLASS) {
			actualType = TypeCast.getWrappedPrimitive(type);
			// 手动拆箱，不通过TypeCast
			//noinspection MagicConstant
			Type primType = Type.primitive(actualType);
			cw.invoke(Opcodes.INVOKEVIRTUAL, type.owner(), primType.toString().concat("Value"), "()".concat(primType.toDesc()));
		}

		switch (op) {
			case inv -> {
				// 我在期待什么... 甚至没有LCONST_M1
				if (actualType == Type.LONG) {
					cw.ldc(-1L);
					cw.insn(LXOR);
				} else {
					cw.insn(ICONST_M1);
					cw.insn(IXOR);
				}
			}
			case logic_not -> {
				// Javac使用跳转
				// JVM中布尔值有两种表示方法：LSB和NZ
				// 这会影响C2的Pattern matching吗
				cw.insn(ICONST_1);
				cw.insn(IXOR);
			}
			case sub -> cw.insn((byte) (INEG - Type.SORT_INT + Math.max(Type.SORT_INT, Type.getSort(actualType))));
		}
	}

	@Override
	public void writeShortCircuit(MethodWriter cw, @NotNull TypeCast.Cast cast, boolean ifThen, @NotNull Label label) {
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
		if (right == null) return "expr.illegalStart";
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