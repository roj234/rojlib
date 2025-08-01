package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.BitSet;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.compiler.runtime.RtUtil;
import roj.concurrent.LazyThreadLocal;
import roj.concurrent.OperationDone;
import roj.config.data.CEntry;
import roj.text.CharList;

import static roj.asm.Opcodes.*;
import static roj.compiler.Tokens.*;

/**
 * AST - 二元操作 a + b
 * @author Roj233
 * @since 2022/2/24 19:56
 */
final class BinaryOp extends Expr {
	final short operator;
	Expr left, right;

	// 对于下列操作，由于范围已知，可以保证它们的类型不会自动提升
	private static final BitSet BIT_OP = BitSet.from(and,or,xor,shr,ushr), BITSHIFT = BitSet.from(shl,shr,ushr);
	private static final byte[] BIT_COUNT = new byte[] {-1, 8, 16, 16, 32, 64};

	private IType type;
	private TypeCast.Cast castLeft, castRight;
	private byte flag;
	// max(max(dataCap(left), dataCap(right)) - 4, 0)
	// 栈上的类型 0=int, 1=long, 2=float, 3=double
	private byte stackType;

	BinaryOp(short op) { this.operator = op; }
	BinaryOp(short op, Expr left, Expr right) {
		this.operator = op;
		this.left = left;
		this.right = right;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();

		if (shouldAddBracket(left, true)) sb.append('(').append(left).append(')');
		else sb.append(left);

		sb.append(' ').append(byId(operator)).append(' ');

		if (shouldAddBracket(right, false)) sb.append('(').append(right).append(')');
		else sb.append(right);

		return sb.toStringAndFree();
	}
	private boolean shouldAddBracket(Expr node, boolean isLeftChild) {
		if (node instanceof LeftValue || node instanceof Invoke) return false;
		if (!(node instanceof BinaryOp childOp)) return true;

		var ep = CompileContext.get().ep;

		int priority = ep.binaryOperatorPriority(this.operator);
		int childPriority = ep.binaryOperatorPriority(childOp.operator);

		if (childPriority > priority) return false;

		if (childPriority == priority) {
			boolean isLeftAssoc = ep.isLeftAssociative(this.operator);
			// 左结合运算符：仅当子节点是右子节点时加括号
			// 右结合运算符：仅当子节点是左子节点时加括号
			return isLeftAssoc != isLeftChild;
		}

		return true;
	}

	private static int getPrimitiveOrWrapperTypeCapacity(IType type) {
		int i = type.getActualType();
		if (i == Type.CLASS) {
			if (type.genericType() != 0) return 8;
			i = TypeCast.getWrappedPrimitive(type);
		}
		return TypeCast.getDataCap(i);
	}

	private static final LazyThreadLocal<Boolean> IN_ANY_BINARY = new LazyThreadLocal<>();
	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {return resolveEx(ctx, false);}
	final Expr resolveEx(CompileContext ctx, boolean isAssignEx) {
		if (type != null) return this;

		IType lType, rType;

		// 字符串加法
		if (operator == add) {
			// 避免无用的resolve(创建大量StringConcat)
			boolean added = IN_ANY_BINARY.get() != Boolean.TRUE;
			if (added) IN_ANY_BINARY.set(true);
			try {
				lType = (left = left.resolve(ctx)).type();
				if ((lType.equals(Types.STRING_TYPE) ||
					(rType = (right = right.resolve(ctx)).type()).equals(Types.STRING_TYPE))) {
					Expr node =
						left instanceof StringConcat ? ((StringConcat) left).append(right) :
						right instanceof StringConcat ? ((StringConcat) right).prepend(left) :
						new StringConcat(left, right);
					return added ? node.resolve(ctx) : node;
				}
			} finally {
				if (added) IN_ANY_BINARY.remove();
			}
		} else {
			lType = (left = left.resolve(ctx)).type();
			rType = (right = right.resolve(ctx)).type();
		}

		if (lType.getActualType() == Type.VOID || rType.getActualType() == Type.VOID) {
			ctx.report(this, Kind.ERROR, "var.voidType", lType.getActualType() == Type.VOID ? left : right);
			return NaE.resolveFailed(this);
		}

		// 常量优化: (A + 1) - 2 转换为 A + (1 - 2)
		if (
			// 1. 满足交换律 (这个函数名称好怪哦) (目前只有加法和乘法, 减法和除法还没做, 可能需要基于策略设计模式)
			ctx.ep.isCommutative(operator) &&
			left instanceof BinaryOp op &&
			// 2. 不是浮点 (NaN / 精度问题)
			// 3. 无副作用 (是常量)
			// 4. 未发生类型提升 (op.type()获取表达式值类型)
			op.right.isConstant() && TypeCast.getDataCap(op.type().getActualType()) <= 4 &&
			this.right.isConstant() && TypeCast.getDataCap(this.right.type().getActualType()) <= 4 &&
			// 5. 操作符优先级相同
			ctx.ep.binaryOperatorPriority(op.operator) == ctx.ep.binaryOperatorPriority(operator)) {

			LavaCompiler.debugLogger().info("doing constant optimize for {}", this);
			left = op.right;
			op.right = this.resolve(ctx);
			return new BinaryOp(op.operator, op.left, this).resolveEx(ctx, isAssignEx);
		}

		type = lType;
		// TODO 第一次赋值可以就是它吗？
		int capL = getPrimitiveOrWrapperTypeCapacity(left.minType());
		int capR = getPrimitiveOrWrapperTypeCapacity(right.minType());
		int dataCap = Math.max(capL, capR);

		primitive: {
			if (dataCap < 8 && (operator < logic_and || operator > nullish_coalescing)) {
				if (Math.min(capL, capR) == 0) {
					if (dataCap != 0 || operator > logic_or || operator < and) {
						ctx.report(this, Kind.ERROR, "op.notApplicable.binary", lType, rType, byId(operator));
						return NaE.resolveFailed(this);
					}
					dataCap = 8; // boolean
				} else {
					if (dataCap < 4 && !BIT_OP.contains(operator)) dataCap = 4;

					if (operator >= shl) {
						// 6 = float, 7 = double
						if (dataCap > 5) {
							ctx.report(this, Kind.ERROR, "op.notApplicable.binary", lType, rType, byId(operator));
							return NaE.resolveFailed(this);
						} else if (dataCap == 5 && BITSHIFT.contains(operator)) {
							// 5 = long
							// lsh, rsh, rsh_unsigned => int bits operator
							castRight = ctx.castTo(rType, Type.primitive(Type.INT), TypeCast.E_NUMBER_DOWNCAST);
							break primitive;
						}
					}

					//noinspection MagicConstant
					type = Type.primitive(TypeCast.getDataCapRev(dataCap));
					castLeft = ctx.castTo(lType, type, TypeCast.E_NUMBER_DOWNCAST);
					castRight = ctx.castTo(rType, type, TypeCast.E_NUMBER_DOWNCAST);
				}

				break primitive;
			}

			switch (operator) {
				case equ, neq:// 无法比较的类型
					if (rType.isPrimitive()) castRight = ctx.castTo(rType, lType, TypeCast.E_DOWNCAST);
					else if (lType.isPrimitive()) castLeft = ctx.castTo(lType, rType, TypeCast.E_DOWNCAST);
					dataCap = 9;
				break;
				case logic_and, logic_or, nullish_coalescing:
					if (operator == nullish_coalescing) {
						if (lType.isPrimitive()) ctx.report(this, Kind.ERROR, "symbol.error.derefPrimitive", lType);
						type = ctx.getCommonParent(lType, rType);
					} else {
						type = Type.primitive(Type.BOOLEAN);
					}

					castLeft = ctx.castTo(lType, type, 0);
					castRight = ctx.castTo(rType, type, 0);
				break;
				default:
					Expr override = ctx.getOperatorOverride(left, right, operator);
					if (override == null) {
						if (isAssignEx) return null;
						ctx.report(this, Kind.ERROR, "op.notApplicable.binary", lType, rType, byId(operator));
						return NaE.resolveFailed(this);
					}
					return override;
			}

			if (castLeft != null && (castLeft.type) < 0 ||
				castRight != null && (castRight.type) < 0) return NaE.resolveFailed(this);
		}

		stackType = (byte) (TypeCast.getDataCap(type.getActualType())-4);
		if (operator >= equ) type = Type.primitive(Type.BOOLEAN);
		if (operator == pow && dataCap == 6) {
			// 将pow float提升到double
			type = Type.primitive(Type.DOUBLE);
			stackType = 3;
			//但是编译期常量就算了
			//dataCap = 7;
		}

		if (!left.isConstant()) {
			if (right.isConstant()) {
				switch (operator) {
					case add, sub, or, xor -> {
						// 可交换位置的二元表达式无需当且仅当…… 是确定运算：int/long，没有类型提升
						if (dataCap <= 4 && castLeft == null && ((CEntry) right.constVal()).asInt() == 0)
							return left;
					}
					// equ 和 neq 应该可以直接删除跳转？反正boolean也就是0和非0 （AND 1就行）
					case equ, neq, lss, geq, gtr, leq -> {
						if (dataCap <= 4 && ((CEntry) right.constVal()).asInt() == 0) {
							flag = 1;
						}
					}
					// expr ?? null => expr
					case nullish_coalescing -> {
						if (right.constVal() == null) {
							ctx.report(this, Kind.WARNING, "binary.uselessNullish");
							return left;
						}
					}
					default -> {
						var expr = checkRight(ctx, dataCap);
						if(expr != null) return expr;
					}
				}
			}
			return this;
		}

		switch (operator) {
			case logic_and, logic_or:
				var exprVal = (boolean) left.constVal();
				var v = exprVal == (operator == logic_and) ? right : Expr.valueOf(exprVal);
				ctx.report(this, Kind.WARNING, "binary.constant", v);
				return v;
			case nullish_coalescing:
				v = left.constVal() == null ? right : left;
				ctx.report(this, Kind.WARNING, "binary.constant", v);
				return v;
		}

		if (!right.isConstant()) {
			switch (operator) {
				case add, sub, or, xor -> {
					if (dataCap <= 4 && castLeft == null && ((CEntry) left.constVal()).asInt() == 0)
						return right;
				}
				case equ, neq, lss, geq, gtr, leq -> {
					if (dataCap <= 4 && ((CEntry) left.constVal()).asInt() == 0) {
						flag = 2;
					}
				}
			}
			return this;
		} else {
			var expr = checkRight(ctx, dataCap);
			if(expr != null) return expr;
		}

		// 现在左右都是常量
		switch (operator) {
			case lss, gtr, geq, leq:
				double l = ((CEntry) left.constVal()).asDouble(), r = ((CEntry) right.constVal()).asDouble();
				return valueOf(switch (operator) {
					case lss -> l < r;
					case gtr -> l > r;
					case geq -> l >= r;
					case leq -> l <= r;
					default -> throw OperationDone.NEVER;
				});

			case equ, neq:
				return valueOf((operator == equ) == (dataCap == 9 ?
					left.constVal().equals(right.constVal()) :
					((CEntry)left.constVal()).asDouble() == ((CEntry)right.constVal()).asDouble()));
		}

		switch (dataCap) {
			case 1, 2, 3, 4: {
				int l = ((CEntry) left.constVal()).asInt(), r = ((CEntry) right.constVal()).asInt();
				int o = switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> RtUtil.pow(l, r);

					case shl -> l<<r;
					case shr -> l>>r;
					case ushr -> l>>>r;
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				};
				return constant(BIT_OP.contains(operator) ? type : Type.primitive(Type.INT), CEntry.valueOf(o));
			}
			case 5: {
				long l = ((CEntry) left.constVal()).asLong(), r = ((CEntry) right.constVal()).asLong();
				return valueOf(CEntry.valueOf(switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> RtUtil.pow(l, r);

					case shl -> l<<r;
					case shr -> l>>r;
					case ushr -> l>>>r;
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				}));
			}
			case 6: {
				float l = ((CEntry) left.constVal()).asFloat(), r = ((CEntry) right.constVal()).asFloat();
				return valueOf(CEntry.valueOf(switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> (float) Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				}));
			}
			case 7: {
				double l = ((CEntry) left.constVal()).asDouble(), r = ((CEntry) right.constVal()).asDouble();
				return valueOf(CEntry.valueOf(switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				}));
			}
			case 8: {
				boolean l = (boolean) left.constVal(), r = (boolean) right.constVal();
				return valueOf(switch (operator) {
					case and -> l&r;
					case or -> l|r;
					case xor -> l^r;
					default -> throw OperationDone.NEVER;
				});
			}
		}

		return this;
	}
	private Expr checkRight(CompileContext ctx, int dataCap) {
		var c = (CEntry) right.constVal();
		switch (operator) {
			case shl, shr, ushr -> {
				int value = c.asInt();
				if (value == 0 && castLeft == null) return left;
				if (value > BIT_COUNT[dataCap]) ctx.report(this, Kind.SEVERE_WARNING, "binary.shiftOverflow", type, BIT_COUNT[dataCap]);
			}
			case div -> {
				switch (stackType) {
					case 0 -> {
						if (c.asInt() == 0) {
							ctx.report(this, Kind.SEVERE_WARNING, "binary.divisionByZero");
							return this;
						}
					}
					case 1 -> {
						if (c.asLong() == 0) {
							ctx.report(this, Kind.SEVERE_WARNING, "binary.divisionByZero");
							return this;
						}
					}
					case 2 -> {
						if (c.asFloat() == 0) {
							ctx.report(this, Kind.WARNING, "binary.divisionByZero");
						}
					}
					case 3 -> {
						if (c.asDouble() == 0) {
							ctx.report(this, Kind.WARNING, "binary.divisionByZero");
						}
					}
				}
			}
		}

		return null;
	}

	@Override
	public IType type() { return type; }

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		switch (operator) {
			// && 和 || 想怎么用，就怎么用！
			case logic_and, logic_or: {
				var shortCircuit = new Label();
				left.writeShortCircuit(cw, castLeft, operator == logic_or, shortCircuit);
				if (noRet) {
					right.write(cw, true);
					cw.label(shortCircuit); // short circuit no LDC present
					return;
				}
				right.write(cw, castRight);

				var sayResult = new Label();
				cw.jump(sayResult);

				cw.label(shortCircuit);
				cw.insn(operator == logic_and ? ICONST_0 : ICONST_1);
				cw.label(sayResult);
				return;
			}
			case nullish_coalescing: {
				mustBeStatement(noRet);

				Label end = new Label();
				left.write(cw, castLeft);
				cw.insn(DUP);
				cw.jump(IFNONNULL, end);
				cw.insn(POP);
				right.write(cw, castRight);
				cw.label(end);
				return;
			}
		}

		mustBeStatement(noRet);
		if (flag != 2) writeLeft(cw);
		if (flag != 1) writeRight(cw);
		writeOperator(cw);
	}

	private static byte invertCode(int code, boolean invert) {
		if (invert) {
			assert code >= IFEQ && code <= IF_acmpne;
			code = IFEQ + ((code-IFEQ) ^ 1); // 草，Opcode的排序还真的很有讲究
		}
		return (byte) code;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void writeShortCircuit(MethodWriter cw, @Nullable TypeCast.Cast cast, boolean ifThen, @NotNull Label far) {
		if (operator < logic_and || operator > leq || operator == nullish_coalescing) {
			super.writeShortCircuit(cw, cast, ifThen, far);
			return;
		}

		switch (operator) {
			case logic_and: {
				var sibling = new Label();
				if (!ifThen) {
					left.writeShortCircuit(cw, castLeft, false, far/*fail*/);
					right.writeShortCircuit(cw, castRight, false, far/*fail*/);
				} else {
					left.writeShortCircuit(cw, castLeft, false, sibling/*fail*/);
					right.writeShortCircuit(cw, castRight, true, far/*success*/);
				}
				cw.label(sibling);
				return;
			}
			case logic_or: {
				var sibling = new Label();
				if (ifThen) {
					left.writeShortCircuit(cw, castLeft, true, far/*success*/);
					right.writeShortCircuit(cw, castRight, true, far/*success*/);
				} else {
					left.writeShortCircuit(cw, castLeft, true, sibling/*success*/);
					right.writeShortCircuit(cw, castRight, false, far/*fail*/);
				}
				cw.label(sibling);
				return;
			}
		}

		var flag = this.flag;
		if (flag != 2) writeLeft(cw);
		if (flag != 1) writeRight(cw);

		int opc = stackType;
		if (opc < 0) opc = 0;

		switch (operator) {
			case equ, neq:
				if (!left.type().isPrimitive() & !right.type().isPrimitive()) {
					opc = IF_acmpeq + (operator - equ);
					break;
				}

			case lss, geq, gtr, leq: {
				opc = writeCmp(cw, opc) + invertOperator();
			}
		}

		cw.jump(invertCode(opc, !ifThen), far);
	}

	private int invertOperator() {
		return (flag == 2 ? switch (operator) {
			case lss -> gtr;
			case geq -> leq;
			case gtr -> lss;
			case leq -> geq;
			default -> operator;
		} : operator) - equ;
	}

	final void writeLeft(MethodWriter cw) {left.write(cw, castLeft);}
	final void writeRight(MethodWriter cw) {right.write(cw, castRight);}
	@SuppressWarnings("fallthrough")
	final void writeOperator(MethodWriter cw) {
		int opc = stackType;
		if (opc < 0) opc = 0;

		var opr = this.operator;
		switch (opr) {
			//default: throw OperationDone.NEVER;
			case add, sub, mul, div, rem: opc += ((opr - add) << 2) + IADD; break;
			case pow: {
				switch (opc) {
					case 0 -> cw.invoke(INVOKESTATIC, RtUtil.CLASS_NAME, "pow", "(II)I");
					case 1 -> cw.invoke(INVOKESTATIC, RtUtil.CLASS_NAME, "pow", "(JJ)J");
					default -> cw.invoke(INVOKESTATIC, "java/util/Math", "pow", "(DD)D");
				}
				return;
			}
			case shl, shr, ushr, and, or, xor: opc += ((opr - shl) << 1) + ISHL; break;
			case equ, neq:
				if (!left.type().isPrimitive() & !right.type().isPrimitive()) {
					jump(cw, opc);
					return;
				}

			case lss, geq, gtr, leq: {
				opc = writeCmp(cw, opc);
				if (stackType == 0 && opr <= neq && flag == 0) {
					cw.insn(ISUB); // (left - right) == 0
					if (opr == equ) {
						cw.insn(ICONST_1);
						cw.insn(IXOR);
					}
				} else {
					jump(cw, opc + invertOperator());
				}
				return;
			}
		}

		cw.insn((byte)opc);
	}
	private int writeCmp(MethodWriter cw, int opc) {
		return switch (opc) {
			case 1 -> {
				cw.insn(LCMP);
				yield IFEQ;
			}
			case 2, 3 -> {
				// 遇到NaN时必须失败(不跳转
				// lss => CMPG
				cw.insn((byte) (FCMPL -4+opc*2 + ((operator-equ)&1)));
				yield IFEQ;
			}
			default -> flag != 0 ? IFEQ : IF_icmpeq;
		};
	}
	private void jump(MethodWriter cw, int code) {
		Label _true = new Label();
		Label end = new Label();
		cw.jump((byte) code, _true);
		cw.insn(ICONST_0);
		cw.jump(end);
		cw.label(_true);
		cw.insn(ICONST_1);
		cw.label(end);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof BinaryOp op)) return false;
		return op.left.equals(o) && op.right.equals(right) && op.operator == operator;
	}

	@Override
	public int hashCode() {
		int result = operator;
		result = 31 * result + left.hashCode();
		result = 31 * result + right.hashCode();
		return result;
	}
}