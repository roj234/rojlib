package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
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
import roj.config.node.ConfigValue;
import roj.text.CharList;
import roj.util.OperationDone;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaTokenizer.*;

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
	private static final byte[] BIT_COUNT = new byte[] {-1, -1, 8, 16, 16, 32, 64};

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

	private static int getPrimitiveOrWrapperSort(IType type) {
		int i = type.getActualType();
		if (i == Type.CLASS) {
			if (type.genericType() != 0) return Type.SORT_OBJECT;
			i = TypeCast.getWrappedPrimitive(type);
			if (i == 0) return Type.SORT_OBJECT;
		}
		return Type.getSort(i);
	}

	private static final int SORT_BOOLEAN_MY = 9, SORT_EQU = 10;
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
			return notApplicable(ctx);
		}

		// 常量优化: (A + 1) - 2 转换为 A + (1 - 2)
		if (
			// 1. 满足交换律 (这个函数名称好怪哦) (目前只有加法和乘法, 减法和除法还没做, 可能需要基于策略设计模式)
			ctx.ep.isCommutative(operator) &&
			left instanceof BinaryOp op &&
			// 2. 不是浮点 (NaN / 精度问题)
			// 3. 无副作用 (是常量)
			// 4. 未发生类型提升 (op.type()获取表达式值类型)
			op.right.isConstant() && Type.getSort(op.type().getActualType()) <= Type.SORT_INT &&
			this.right.isConstant() && Type.getSort(this.right.type().getActualType()) <= Type.SORT_INT &&
			// 5. 操作符优先级相同
			ctx.ep.binaryOperatorPriority(op.operator) == ctx.ep.binaryOperatorPriority(operator)) {

			LavaCompiler.debugLogger().info("doing constant optimize for {}", this);
			left = op.right;
			op.right = this.resolve(ctx);
			return new BinaryOp(op.operator, op.left, this).resolveEx(ctx, isAssignEx);
		}

		type = lType;
		castLeft = TypeCast.Cast.IDENTITY;
		castRight = TypeCast.Cast.IDENTITY;
		// TODO 第一次赋值可以就是它吗？
		int capL = getPrimitiveOrWrapperSort(left.minType());
		int capR = getPrimitiveOrWrapperSort(right.minType());
		int sort = Math.max(capL, capR);

		primitive: {
			if (sort < Type.SORT_OBJECT && (operator < logic_and || operator > nullish_coalescing)) {
				if (Math.min(capL, capR) == 0) {
					if (sort != 0 || operator > logic_or || operator < and) {
						return notApplicable(ctx);
					}
					sort = SORT_BOOLEAN_MY;
				} else {
					if (sort < Type.SORT_INT && !BIT_OP.contains(operator)) sort = Type.SORT_INT;

					if (operator >= shl) {
						// float, double
						if (sort > Type.SORT_LONG) {
							return notApplicable(ctx);
						} else if (sort == Type.SORT_LONG && BITSHIFT.contains(operator)) {
							// lsh, rsh, rsh_unsigned => int bits operator
							castRight = ctx.castTo(rType, Type.INT_TYPE, TypeCast.LOSSY);
							break primitive;
						}
					}

					//noinspection MagicConstant
					type = Type.primitive(Type.getBySort(sort));
					castLeft = ctx.castTo(lType, type, TypeCast.LOSSY);
					castRight = ctx.castTo(rType, type, TypeCast.LOSSY);
				}

				break primitive;
			}

			switch (operator) {
				case equ, neq:// 无法比较的类型
					if (rType.isPrimitive()) castLeft = ctx.castTo(lType, type = rType, TypeCast.DOWNCAST);
					else if (lType.isPrimitive()) castRight = ctx.castTo(rType, type = lType, TypeCast.DOWNCAST);
					sort = SORT_EQU;
				break;
				case logic_and, logic_or, nullish_coalescing:
					if (operator == nullish_coalescing) {
						if (lType.isPrimitive()) ctx.report(this, Kind.ERROR, "symbol.derefPrimitive", lType);
						type = ctx.getCommonParent(lType, rType);
					} else {
						type = Type.BOOLEAN_TYPE;
					}

					castLeft = ctx.castTo(lType, type, 0);
					castRight = ctx.castTo(rType, type, 0);
				break;
				default:
					if (isAssignEx) return null;
					return notApplicable(ctx);
			}

			if (castLeft != null && (castLeft.type) < 0 ||
				castRight != null && (castRight.type) < 0) return NaE.resolveFailed(this);
		}

		stackType = (byte) (Type.getSort(type.getActualType())-Type.SORT_INT);
		if (operator >= equ) type = Type.BOOLEAN_TYPE;
		if (operator == pow && sort == Type.SORT_INT) {
			// 将pow float提升到double
			type = Type.DOUBLE_TYPE;
			stackType = 3;
			//但是编译期常量就算了
			//sort = Type.SORT_DOUBLE;
		}

		if (!left.isConstant()) {
			if (right.isConstant()) {
				switch (operator) {
					case add, sub, or, xor -> {
						// 可交换位置的二元表达式无需当且仅当…… 是确定运算：int/long，没有类型提升
						if (sort <= Type.SORT_INT && castLeft == null && ((ConfigValue) right.constVal()).asInt() == 0)
							return left;
					}
					// equ 和 neq 应该可以直接删除跳转？反正boolean也就是0和非0 （AND 1就行）
					case equ, neq, lss, geq, gtr, leq -> {
						if (sort <= Type.SORT_INT && ((ConfigValue) right.constVal()).asInt() == 0) {
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
						var expr = checkRight(ctx, sort);
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
					if (sort <= Type.SORT_INT && castLeft == null && ((ConfigValue) left.constVal()).asInt() == 0)
						return right;
				}
				case equ, neq, lss, geq, gtr, leq -> {
					if (sort <= Type.SORT_INT && ((ConfigValue) left.constVal()).asInt() == 0) {
						flag = 2;
					}
				}
			}
			return this;
		} else {
			var expr = checkRight(ctx, sort);
			if(expr != null) return expr;
		}

		// 现在左右都是常量
		switch (operator) {
			case lss, gtr, geq, leq:
				double l = ((ConfigValue) left.constVal()).asDouble(), r = ((ConfigValue) right.constVal()).asDouble();
				return valueOf(switch (operator) {
					case lss -> l < r;
					case gtr -> l > r;
					case geq -> l >= r;
					case leq -> l <= r;
					default -> throw OperationDone.NEVER;
				});

			case equ, neq:
				return valueOf((operator == equ) == (sort == SORT_EQU ?
					left.constVal().equals(right.constVal()) :
					((ConfigValue)left.constVal()).asDouble() == ((ConfigValue)right.constVal()).asDouble()));
		}

		switch (sort) {
			case Type.SORT_BYTE, Type.SORT_CHAR, Type.SORT_SHORT, Type.SORT_INT: {
				int l = ((ConfigValue) left.constVal()).asInt(), r = ((ConfigValue) right.constVal()).asInt();
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
				return constant(BIT_OP.contains(operator) ? type : Type.INT_TYPE, ConfigValue.valueOf(o));
			}
			case Type.SORT_LONG: {
				long l = ((ConfigValue) left.constVal()).asLong(), r = ((ConfigValue) right.constVal()).asLong();
				return valueOf(ConfigValue.valueOf(switch (operator) {
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
			case Type.SORT_FLOAT: {
				float l = ((ConfigValue) left.constVal()).asFloat(), r = ((ConfigValue) right.constVal()).asFloat();
				return valueOf(ConfigValue.valueOf(switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> (float) Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				}));
			}
			case Type.SORT_DOUBLE: {
				double l = ((ConfigValue) left.constVal()).asDouble(), r = ((ConfigValue) right.constVal()).asDouble();
				return valueOf(ConfigValue.valueOf(switch (operator) {
					case add -> l+r;
					case sub -> l-r;
					case mul -> l*r;
					case div -> l/r;
					case rem -> l%r;
					case pow -> Math.pow(l, r);
					default -> throw OperationDone.NEVER;
				}));
			}
			case SORT_BOOLEAN_MY: {
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

	@NotNull
	private Expr notApplicable(CompileContext ctx) {
		Expr override = ctx.getOperatorOverride(left, right, operator);
		if (override != null) return override;

		ctx.report(this, Kind.ERROR, "op.notApplicable.binary", byId(operator), left.type(), right.type());
		return NaE.resolveFailed(this);
	}

	private Expr checkRight(CompileContext ctx, int dataCap) {
		var c = (ConfigValue) right.constVal();
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
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		switch (operator) {
			// && 和 || 想怎么用，就怎么用！
			case logic_and, logic_or: {
				var shortCircuit = new Label();
				left.writeShortCircuit(cw, castLeft, operator == logic_or, shortCircuit);
				if (cast == NORET) {
					right.write(cw, NORET);
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
				mustBeStatement(cast);

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

		mustBeStatement(cast);
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
	public void writeShortCircuit(MethodWriter cw, @NotNull TypeCast.Cast cast, boolean ifThen, @NotNull Label far) {
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
					jump(cw, IF_acmpeq + opr - equ);
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