package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;
import roj.text.CharList;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2022/2/24 19:56
 */
final class Binary extends ExprNode {
	final short operator;
	ExprNode left, right;

	// 对于下列操作，由于范围已知，可以保证它们的类型不会自动变int
	private static final MyBitSet KNOWN_NUMBER_STATE = MyBitSet.from(and,or,xor,rsh);
	private IType type;
	private TypeCast.Cast castLeft, castRight;
	private byte flag, dType;

	Binary(short op) { this.operator = op; }
	// assign operation
	Binary(short op, ExprNode left, ExprNode right) {
		this.operator = op;
		this.left = left;
		this.right = right;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();

		if (shouldAddBracket(left)) sb.append('(').append(left).append(')');
		else sb.append(left);

		sb.append(' ').append(byId(operator)).append(' ');

		if (shouldAddBracket(right)) sb.append('(').append(right).append(')');
		else sb.append(right);

		return sb.toStringAndFree();
	}
	private boolean shouldAddBracket(ExprNode node) {
		ExprParser ep = LocalContext.get().ep;
		if (node.isConstant() || node instanceof VarNode || node instanceof Invoke) return false;
		return !(node instanceof Binary n) || ep.binaryOperatorPriority(n.operator) > ep.binaryOperatorPriority(operator);
	}

	private static final ThreadLocal<Boolean> IN_ANY_BINARY = new ThreadLocal<>();
	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (type != null) return this;

		IType lType, rType;

		// 字符串加法
		if (operator == add) {
			// 避免无用的resolve(创建大量StringConcat)
			boolean _add = IN_ANY_BINARY.get() != Boolean.TRUE;
			if (_add) IN_ANY_BINARY.set(true);
			try {
				lType = (left = left.resolve(ctx)).type();
				if ((lType.equals(Constant.STRING) ||
					(rType = (right = right.resolve(ctx)).type()).equals(Constant.STRING))) {
					ExprNode node =
						left instanceof StringConcat ? ((StringConcat) left).append(right) :
						right instanceof StringConcat ? ((StringConcat) right).prepend(left) :
						new StringConcat(left, right);
					return _add ? node.resolve(ctx) : node;
				}
			} finally {
				if (_add) IN_ANY_BINARY.remove();
			}
		} else {
			lType = (left = left.resolve(ctx)).type();
			rType = (right = right.resolve(ctx)).type();
		}

		if (lType.getActualType() == Type.VOID || rType.getActualType() == Type.VOID) {
			ctx.report(Kind.ERROR, "binary.error.void");
			return NaE.RESOLVE_FAILED;
		}

		// (A + 1) - 2 => A + (1 - 2)
		// 1. 操作符等级相同
		// 2. 不能是浮点 (NaN / 精度问题)
		// 3. 变量不能移动
		if (left instanceof Binary br &&
			br.right.isConstant() && TypeCast.getDataCap(br.right.type().getActualType()) <= 4 &&
			ctx.ep.binaryOperatorPriority(br.operator) == ctx.ep.binaryOperatorPriority(operator) ) {

			left = br.right;
			br.right = this.resolve(ctx);
			return br;
		}

		type = lType;
		int dpType = KNOWN_NUMBER_STATE.contains(operator) ? 0 : 4;

		primitive: {
			IType plType = lType.isPrimitive() ? lType : rType;
			if (plType.isPrimitive() && (operator < logic_and || operator > nullish_consolidating)) {
				int cap = TypeCast.getDataCap(plType.getActualType());
				if ((cap&7) != 0) dpType = Math.max(cap, dpType);
				else dpType = 8; // boolean

				IType prType = plType == lType ? rType : lType;
				if (prType.isPrimitive()) {
					cap = TypeCast.getDataCap(prType.getActualType());
					if ((cap & 7) != 0) {
						if (dpType != cap) {
							if (dpType < cap) {
								dpType = cap;

								type = prType;
								TypeCast.Cast cast = ctx.castTo(plType, prType, TypeCast.E_NUMBER_DOWNCAST);
								if (plType == lType) castLeft = cast;
								else castRight = cast;
							} else {
								type = plType;
								TypeCast.Cast cast = ctx.castTo(prType, plType, TypeCast.E_NUMBER_DOWNCAST);
								if (plType == lType) castRight = cast;
								else castLeft = cast;
							}
						}
					} else if (dpType != 8 || operator > logic_or || operator < and) {
						ctx.report(Kind.ERROR, "binary.error.notApplicable", lType, rType, byId(operator));
						return NaE.RESOLVE_FAILED;
					}

					break primitive;
				} else if (prType.genericType() == 0) {
					int wrType = TypeCast.getWrappedPrimitive(prType.rawType());
					if (wrType != 0) {
						cap = TypeCast.getDataCap(wrType);
						if (cap > dpType)
							//noinspection MagicConstant
							type = Type.std(wrType);
						else type = plType;
						castLeft = ctx.castTo(lType, type, TypeCast.E_NUMBER_DOWNCAST);
						castRight = ctx.castTo(rType, type, TypeCast.E_NUMBER_DOWNCAST);

						break primitive;
					}
				}
			}

			switch (operator) {
				case equ, neq:// 无法比较的类型
					if (rType.isPrimitive()) castRight = ctx.castTo(rType, lType, TypeCast.E_DOWNCAST);
					else if (lType.isPrimitive()) castLeft = ctx.castTo(lType, rType, TypeCast.E_DOWNCAST);
					dpType = 9;
				break;
				case logic_and, logic_or, nullish_consolidating:
					if (operator == nullish_consolidating) {
						if (lType.isPrimitive()) ctx.report(Kind.ERROR, "symbol.error.derefPrimitive", lType);
						type = ctx.getCommonParent(lType, rType);
					} else {
						type = Type.std(Type.BOOLEAN);
					}

					castLeft = ctx.castTo(lType, type, 0);
					castRight = ctx.castTo(rType, type, 0);
				break;
				default:
					ExprNode override = ctx.getOperatorOverride(left, right, operator);
					if (override == null) {
						ctx.report(Kind.ERROR, "binary.error.notApplicable", lType, rType, byId(operator));
						return NaE.RESOLVE_FAILED;
					}
					return override;
			}

			if (castLeft != null && (castLeft.type) < 0 ||
				castRight != null && (castRight.type) < 0) return NaE.RESOLVE_FAILED;
		}

		dType = (byte) (TypeCast.getDataCap(type.rawType().type)-4);
		if (operator >= equ) type = Type.std(Type.BOOLEAN);

		if (!left.isConstant()) {
			if (right.isConstant()) {
				switch (operator) {
					// equ 和 neq 应该可以直接删除跳转？反正boolean也就是0和非0 （AND 1就行）
					case equ, neq, lss, geq, gtr, leq -> {
						if (dpType <= 4 && ((AnnVal) right.constVal()).asInt() == 0) {
							flag = 1;
						}
						return this;
					}
					// expr ?? null => expr
					case nullish_consolidating -> {
						if (right.constVal() == null) {
							ctx.report(Kind.WARNING, "binary.uselessNullish");
							return left;
						}
					}
					default -> checkDivZero(right, ctx);
				}
			}
			return this;
		}

		switch (operator) {
			default: checkDivZero(right, ctx); break;
			case logic_and, logic_or:
				var exprVal = (boolean) left.constVal();
				var v = exprVal == (operator == logic_and) ? right : Constant.valueOf(exprVal);
				ctx.report(Kind.WARNING, "binary.constant", v);
				return v;
			case nullish_consolidating:
				v = left.constVal() == null ? right : left;
				ctx.report(Kind.WARNING, "binary.constant", v);
				return v;
		}

		if (!right.isConstant()) {
			switch (operator) {
				case equ, neq, lss, geq, gtr, leq: {
					if (dpType <= 4 && ((AnnVal) left.constVal()).asInt() == 0) {
						flag = 2;
					}
				}
			}
			return this;
		}

		switch (operator) {
			case lss, gtr, geq, leq:
				double l = ((AnnVal) left.constVal()).asDouble(), r = ((AnnVal) right.constVal()).asDouble();
				return Constant.valueOf(switch (operator) {
					case lss -> l < r;
					case gtr -> l > r;
					case geq -> l >= r;
					case leq -> l <= r;
					default -> throw OperationDone.NEVER;
				});

			case equ, neq:
				return Constant.valueOf((operator == equ) == (dpType == 9 ?
					left.constVal().equals(right.constVal()) :
					((AnnVal)left.constVal()).asDouble() == ((AnnVal)right.constVal()).asDouble()));
		}

		switch (dpType) {
			case 1, 2, 3, 4: {
				int l = ((AnnVal) left.constVal()).asInt(), r = ((AnnVal) right.constVal()).asInt();
				int o = switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case mod -> l%r;
					case pow -> (int) Math.pow(l, r);

					case lsh -> l<<r;
					case rsh -> l>>r;
					case rsh_unsigned -> l>>>r;
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				};
				return new Constant(type, AnnVal.valueOf(o));
			}
			case 5: {
				long l = ((AnnVal) left.constVal()).asLong(), r = ((AnnVal) right.constVal()).asLong();
				long o = switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case mod -> l%r;
					case pow -> (long) Math.pow(l, r);

					case lsh -> l<<r;
					case rsh -> l>>r;
					case rsh_unsigned -> l>>>r;
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				};
				return new Constant(Type.std(Type.LONG), AnnVal.valueOf(o));
			}
			case 6: {
				float l = ((AnnVal) left.constVal()).asFloat(), r = ((AnnVal) right.constVal()).asFloat();
				float o = switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case mod -> l%r;
					case pow -> (float) Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				};
				return new Constant(Type.std(Type.FLOAT), AnnVal.valueOf(o));
			}
			case 7: {
				double l = ((AnnVal) left.constVal()).asDouble(), r = ((AnnVal) right.constVal()).asDouble();
				double o = switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case mod -> l%r;
					case pow -> Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				};
				return new Constant(Type.std(Type.DOUBLE), AnnVal.valueOf(o));
			}
			case 8: {
				boolean l = (boolean) left.constVal(), r = (boolean) right.constVal();
				boolean o = switch (operator) {
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				};
				return new Constant(Type.std(Type.BOOLEAN), o);
			}
		}

		return this;
	}

	private void checkDivZero(ExprNode node, LocalContext ctx) {
		if (dType <= 1 && operator == div && ((AnnVal) node.constVal()).asInt() == 0) {
			ctx.report(Kind.WARNING, "binary.divisionByZero");
		}
	}

	@Override
	public IType type() { return type; }

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		switch (operator) {
			// && 和 || 想怎么用，就怎么用！
			case logic_and, logic_or: {
				Label end = new Label();
				int id = cw.beginJumpOn(operator == logic_or, end);
				left.writeDyn(cw, castLeft);
				cw.endJumpOn(id);
				right.writeDyn(cw, castRight);

				if (noRet) {
					cw.label(end);
				} else {
					Label realEnd = new Label();
					cw.jump(realEnd);
					cw.label(end);
					cw.one((byte) (operator-logic_and + ICONST_0));
					cw.label(realEnd);
				}
				return;
			}
			case nullish_consolidating: {
				mustBeStatement(noRet);

				Label end = new Label();
				left.writeDyn(cw, castLeft);
				cw.one(DUP);
				cw.jump(IFNONNULL, end);
				cw.one(POP);
				right.writeDyn(cw, castRight);
				cw.label(end);
				return;
			}
		}

		mustBeStatement(noRet);
		if (flag != 2) writeLeft(cw);
		if (flag != 1) writeRight(cw);
		writeOperator(cw);
	}
	final void writeLeft(MethodWriter cw) {
		left.writeDyn(cw, castLeft);
	}
	final void writeRight(MethodWriter cw) {
		right.writeDyn(cw, castRight);
	}
	@SuppressWarnings("fallthrough")
	final void writeOperator(MethodWriter cw) {
		int opc = dType;
		if (opc < 0) opc = 0;

		switch (operator) {
			default: throw OperationDone.NEVER;
			case add, sub, mul, div, mod: opc += ((operator - add) << 2) + IADD; break;
			case pow: throw new IllegalArgumentException("pow未实现");
			case lsh, rsh, rsh_unsigned, and, or, xor: opc += ((operator - lsh) << 1) + ISHL; break;
			case equ, neq:
				if (!left.type().isPrimitive() & !right.type().isPrimitive()) {
					if (!cw.jumpOn(opc = IF_acmpeq + (operator - equ))) {
						jump(cw, opc);
					}
					return;
				}

			case lss, geq, gtr, leq: {
				switch (opc) {
					case 1 -> {
						cw.one(LCMP);
						opc = IFEQ;
					}
					case 2, 3 -> {
						// 遇到NaN时必须失败(不跳转
						// lss => CMPG
						cw.one((byte) (FCMPL -4+opc*2 + ((operator-equ)&1)));
						opc = IFEQ;
					}
					default -> opc = flag != 0 ? IFEQ : IF_icmpeq;
				}

				if (!cw.jumpOn(opc += (operator - equ))) {
					if (dType == 0 && operator <= neq && flag == 0) {
						cw.one(ISUB); // (left - right) == 0
						if (operator == equ) {
							cw.one(ICONST_1);
							cw.one(IXOR);
						}
					} else {
						jump(cw, opc);
					}
				}
				return;
			}
		}

		cw.one((byte)opc);
	}
	private void jump(MethodWriter cw, int code) {
		Label _true = new Label();
		Label end = new Label();
		cw.jump((byte) code, _true);
		cw.one(ICONST_0);
		cw.jump(end);
		cw.label(_true);
		cw.one(ICONST_1);
		cw.label(end);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Binary b)) return false;
		return b.left.equals(o) && b.right.equals(right) && b.operator == operator;
	}

	@Override
	public int hashCode() {
		int result = operator;
		result = 31 * result + left.hashCode();
		result = 31 * result + right.hashCode();
		return result;
	}
}