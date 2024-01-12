package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;
import roj.text.CharList;

import javax.tools.Diagnostic;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2022/2/24 19:56
 */
final class Binary implements ExprNode {
	final short operator;
	ExprNode left, right;

	// 对于下列操作，由于范围已知，可以保证它们的类型不会自动变int
	private static final MyBitSet KNOWN_NUMBER_STATE = MyBitSet.from(and,or,xor,lsh,rsh,rsh_unsigned);
	private IType type;
	private TypeCast.Cast castLeft, castRight;
	private byte flag;

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

		sb.append(byId(operator));

		if (shouldAddBracket(right)) sb.append('(').append(right).append(')');
		else sb.append(right);

		return sb.toStringAndFree();
	}
	private boolean shouldAddBracket(ExprNode node) {
		return !node.isConstant() && (!(node instanceof Binary) ||
			(binaryOperatorPriority(((Binary) node).operator) > binaryOperatorPriority(operator)));
	}

	private static final ThreadLocal<Boolean> IN_ANY_BINARY = new ThreadLocal<>();
	@NotNull
	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
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
			ctx.report(Diagnostic.Kind.ERROR, "binary.error.void");
			return this;
		}

		// (A + 1) - 2 => A + (1 - 2)
		// 1. 操作符等级相同
		// 2. 不能是浮点 (NaN / 精度问题)
		// 3. 变量不能移动
		if (left instanceof Binary br &&
			br.right.isConstant() && TypeCast.getDataCap(br.right.type().getActualType()) <= 4 &&
			JavaLexer.binaryOperatorPriority(br.operator) == JavaLexer.binaryOperatorPriority(operator) ) {

			left = br.right;
			br.right = this.resolve(ctx);
			return br;
		}

		type = lType;
		int dpType = KNOWN_NUMBER_STATE.contains(operator) ? 0 : 4;

		primitive: {
			IType plType = lType.isPrimitive() ? lType : rType;
			if (plType.isPrimitive()) {
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
					} else if (dpType != 8 || operator > xor || operator < and) {
						ctx.report(Diagnostic.Kind.ERROR, "binary.error.not_applicable", lType, rType, byId(operator));
						return this;
					}

					break primitive;
				} else if (prType.genericType() == 0) {
					int wrType = TypeCast.getWrappedPrimitive(prType.rawType());
					if (wrType != 0) {
						cap = TypeCast.getDataCap(wrType);
						if (cap > dpType) type = Type.std(wrType);
						else type = plType;
						castLeft = ctx.castTo(lType, type, TypeCast.E_NUMBER_DOWNCAST);
						castRight = ctx.castTo(rType, type, TypeCast.E_NUMBER_DOWNCAST);

						break primitive;
					}
				}
			}

			switch (operator) {
				case equ: case neq:
					// 无法比较的类型
					if (rType.isPrimitive()) castRight = ctx.castTo(rType, lType, TypeCast.E_DOWNCAST);
					else if (lType.isPrimitive()) castLeft = ctx.castTo(lType, rType, TypeCast.E_DOWNCAST);
					type = Type.std(Type.BOOLEAN);
					dpType = 0;
					break;
				case logic_and: case logic_or:
					castLeft = ctx.castTo(lType, Type.std(Type.BOOLEAN), 0);
					castRight = ctx.castTo(rType, Type.std(Type.BOOLEAN), 0);
					break;
				default:
					MethodNode override = ctx.getBinaryOverride(lType, rType, operator);
					if (override == null) {
						ctx.report(Diagnostic.Kind.ERROR, "binary.error.not_applicable", lType, rType, byId(operator));
						return this;
					}
					String name = override.name();
					// 反转
					if ((override.modifier()&ACC_STATIC) != 0 && name.endsWith("\0INVERT")) {
						override.name(name.substring(0, name.length()-7));
						return Invoke.binaryAlt(override, right, left);
					}
					return Invoke.binaryAlt(override, left, right);
			}
		}

		if (!left.isConstant()) {
			if (right.isConstant()) {
				switch (operator) {
					// equ 和 neq 应该可以直接删除跳转？反正boolean也就是0和非0 （AND 1就行）
					case equ: case neq: case lss: case geq: case gtr: case leq: {
						if (dpType <= 4 && ((AnnVal) right.constVal()).asInt() == 0) {
							flag = 1;
						}
						return this;
					}
				}
			}
			return this;
		}
		if (!right.isConstant()) {
			switch (operator) {
				case equ: case neq: case lss: case geq: case gtr: case leq: {
					if (dpType <= 4 && ((AnnVal) left.constVal()).asInt() == 0) {
						flag = 2;
					}
					return this;
				}
				case logic_and:
					ctx.report(Diagnostic.Kind.WARNING, "binary.warn.always");
					return ((boolean) left.constVal()) ? right : Constant.valueOf(false);
				case logic_or:
					ctx.report(Diagnostic.Kind.WARNING, "binary.warn.always");
					return ((boolean) left.constVal()) ? Constant.valueOf(true) : right;
				default: return this;
			}
		}

		switch (operator) {
			case lss: case gtr: case geq: case leq:
				double l = ((AnnVal) left.constVal()).asDouble(), r = ((AnnVal) right.constVal()).asDouble();
				boolean v = switch (operator) {
					case lss -> l < r;
					case gtr -> l > r;
					case geq -> l >= r;
					case leq -> l <= r;
					default -> throw OperationDone.NEVER;
				};
				return Constant.valueOf(v);

			case equ: case neq:
				return Constant.valueOf((operator == equ) == (dpType == 0 ?
					left.constVal().equals(right.constVal()) :
					((AnnVal)left.constVal()).asDouble() == ((AnnVal)right.constVal()).asDouble()));
		}

		switch (dpType) {
			case 1: case 2: case 3: case 4: {
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

	@Override
	public IType type() { return type; }

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		switch (operator) {
			// && 和 || 想怎么用，就怎么用！
			case logic_and: case logic_or: {
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
		int opc = TypeCast.getDataCap(type.rawType().type)-4;
		if (opc < 0) opc = 0;

		switch (operator) {
			default: throw OperationDone.NEVER;
			case add: case sub: case mul: case div: case mod: opc += ((operator - add) << 2) + IADD; break;
			case pow: throw new IllegalArgumentException("pow未实现");
			case lsh: case rsh: case rsh_unsigned: case and: case or: case xor: opc += ((operator - lsh) << 1) + ISHL; break;
			case equ: case neq:
				if (!left.type().isPrimitive() & !right.type().isPrimitive()) {
					if (!cw.jumpOn(opc = IF_acmpeq + (operator - equ))) {
						jump(cw, opc);
					}
					return;
				}

			case lss: case geq: case gtr: case leq: {
				switch (opc) {
					case 1:
						cw.one(LCMP);
						opc += IFEQ;
						break;
					case 2: case 3:
						// 遇到NaN时必须失败(不跳转
						// lss => CMPG
						cw.one((byte) (FCMPL - 1 + opc - ((operator-equ)&1)));
						opc += IFEQ;
						break;
					default:
						opc += flag != 0 ? IFEQ : IF_icmpeq;
						break;
				}
				if (!cw.jumpOn(opc += (operator - equ))) {
					if (operator <= neq) {
						cw.one(ICONST_1);
						cw.one(IAND);
						if (operator == equ) {
							// TODO 这能提升多少呢...
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
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof Binary)) return false;
		Binary b = (Binary) o;
		return b.left.equalTo(o) && b.right.equalTo(right) && b.operator == operator;
	}
}