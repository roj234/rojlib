package roj.mildwind.parser.ast;

import roj.asm.visitor.Label;
import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsNull;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

import static roj.asm.Opcodes.*;
import static roj.mildwind.parser.JSLexer.*;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Binary implements Expression {
	final short op;
	Expression left, right;
	Label target;

	public Binary(short op, Expression left, Expression right) {
		switch (op) {
			default: throw new IllegalArgumentException("Unsupported operator " + byId(op));
			case add: case sub:
			case mul: case div: case mod: case pow:
			case and: case or: case xor:
			case lsh: case rsh: case rsh_unsigned:
			case logic_and: case logic_or:
			case lss: case gtr: case leq: case geq:
			case equ: case neq: case feq:
			case nullish_coalescing: break;
		}

		this.op = op;
		this.left = left;
		this.right = right;
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if (noRet) {
			switch (op) {
				case logic_or: case logic_and: break;
				default: throw new NotStatementException();
			}
		}

		left.write(tree, false);

		switch (op) {
			case nullish_coalescing:
			case logic_and: case logic_or:
				int id = tree.getTmpVar();
				tree.var(ASTORE, id);

				tree.var(ALOAD, id);
				if (op == nullish_coalescing) tree.clazz(INSTANCEOF, "roj/mildwind/type/JsNull");
				else tree.invokeV("roj/mildwind/type/JsObject", "asBool", "()I");

				Label end = new Label();
				tree.jump(op != logic_or ? IFEQ : IFNE, end);

				tree.one(POP);
				right.write(tree, false);

				tree.label(end);
				return;
		}

		right.write(tree, false);
		writeOperator(tree);
	}

	static void invokeI(String name, JsMethodWriter tree) { tree.invokeV("roj/mildwind/type/JsObject", name, "(Lroj/mildwind/type/JsObject;)I"); }
	static void invokeZ(String name, JsMethodWriter tree) { tree.invokeV("roj/mildwind/type/JsObject", name, "(Lroj/mildwind/type/JsObject;)Z"); }
	static void invoke2(String name, JsMethodWriter tree) { tree.invokeS("roj/mildwind/type/JsObject", name, "(Lroj/mildwind/type/JsObject;Lroj/mildwind/type/JsObject;)Lroj/mildwind/type/JsObject;"); }
	static void invoke1(String name, JsMethodWriter tree) { tree.invokeV("roj/mildwind/type/JsObject", name, "(Lroj/mildwind/type/JsObject;)Lroj/mildwind/type/JsObject;"); }

	void writeOperator(JsMethodWriter tree) {
		switch (op) {
			// 条件不成立时跳转，所以是反的
			case lss: case leq:
				invokeI("op_leq", tree);
				if (target == null) {
					tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
				} else tree.jump(op == leq ? IFGT : IFGE, target);
			break;
			case gtr: case geq:
				invokeI("op_geq", tree);
				if (target == null) {
					tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
				} else tree.jump(op == geq ? IFLT : IFLE, target);
			break;
			case equ: case neq:
				invokeZ("op_equ", tree);
				if (target == null) {
					if (op == neq) {
						tree.ldc(1);
						tree.one(IXOR);
					}
					tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
				} else tree.jump(op == neq ? IFNE : IFEQ, target);
			break;
			case feq: case nfeq:
				invokeZ("op_feq", tree);
				if (target == null) {
					if (op == nfeq) {
						tree.ldc(1);
						tree.one(IXOR);
					}
					tree.invokeS("roj/mildwind/type/JsBool", "valueOf", "(I)Lroj/mildwind/type/JsObject;");
				} else {
					tree.jump(op == nfeq ? IFNE : IFEQ, target);
				}
			break;

			default: throw new IllegalStateException();

			case add: invoke1("op_add", tree); break;
			case sub: invoke1("op_sub", tree); break;
			case mod: invoke1("op_mod", tree); break;
			case div: invoke1("op_div", tree); break;
			case mul: invoke1("op_mul", tree); break;
			case pow: invoke1("op_pow", tree); break;

			case and: invoke2("op_bitand", tree); break;
			case or : invoke2("op_bitor", tree); break;
			case xor: invoke2("op_bitxor", tree); break;
			case lsh: invoke2("op_ishl", tree); break;
			case rsh: invoke2("op_ishr", tree); break;
			case rsh_unsigned: invoke2("op_iushr", tree); break;
		}
	}

	@Nonnull
	@Override
	public Expression compress() {
		boolean lc = (left = left.compress()).isConstant();
		boolean rc = (right = right.compress()).isConstant();
		if (lc) {
			if (!rc) {
				switch (op) {
					case logic_and: return left.constVal().asBool() != 0 ? new AsBool(right) : Constant.valueOf(false);
					case logic_or: return left.constVal().asBool() != 0 ? Constant.valueOf(left.constVal()) : right;
				}
				return this;
			}
		} else {
			return this;
		}

		return Constant.valueOf(compute(left.constVal(), right.constVal()));
	}

	private JsObject compute(JsObject l, JsObject r) {
		switch (op) {
			default:
			case lss: return l.op_leq(r) < 0 ? JsBool.TRUE : JsBool.FALSE;
			case leq: return l.op_leq(r) <= 0 ? JsBool.TRUE : JsBool.FALSE;
			case gtr: return l.op_geq(r) > 0 ? JsBool.TRUE : JsBool.FALSE;
			case geq: return l.op_geq(r) >= 0 ? JsBool.TRUE : JsBool.FALSE;
			case equ: return l.op_equ(r) ? JsBool.TRUE : JsBool.FALSE;
			case neq: return !l.op_equ(r) ? JsBool.TRUE : JsBool.FALSE;
			case feq: return l.op_feq(r) ? JsBool.TRUE : JsBool.FALSE;

			case nullish_coalescing: return l instanceof JsNull ? r : l;
			case logic_and: return l.asBool() == 0 ? l : r;
			case logic_or: return l.asBool() != 0 ? l : r;

			case add: return l.op_add(r);
			case sub: return l.op_sub(r);
			case mul: return l.op_mul(r);
			case div: return l.op_div(r);
			case mod: return l.op_mod(r);
			case pow: return l.op_pow(r);

			case and: return JsObject.op_bitand(l, r);
			case or: return JsObject.op_bitor(l, r);
			case xor: return JsObject.op_bitxor(l, r);
			case lsh: return JsObject.op_ishl(l, r);
			case rsh: return JsObject.op_ishr(l, r);
			case rsh_unsigned: return JsObject.op_iushr(l, r);
		}
	}

	@Override
	public JsObject compute(JsObject ctx) { return compute(left.compute(ctx), right.compute(ctx)); }

	@Override
	@SuppressWarnings("fallthrough")
	public Type type() {
		switch (op) {
			default:
			case lss: case leq:
			case gtr: case geq:
			case equ: case neq: case feq:
				return Type.BOOL;

			case logic_and: case logic_or: return left.type() == right.type() ? left.type() : Type.OBJECT;

			case and: case or: case xor:
			case lsh: case rsh: case rsh_unsigned:
				return Type.INT;

			case add:
				// str
				Type lt = left.type();
				Type rt = right.type();
				if (!lt.numOrBool()||!rt.numOrBool()) {
					if (lt != Type.NAN && rt != Type.NAN) return Type.STRING;
				}

			case sub:
			case mul: case div:
			case mod: case pow:
				return right.type() == Type.INT && left.type() == Type.INT ? Type.INT : Type.DOUBLE;
		}
	}

	@Override
	public String toString() {
		String l = left.toString();
		if (!left.isConstant()) l = '('+l+')';
		String r = right.toString();
		if (!right.isConstant()) r = '('+r+')';
		return l + byId(op) + r;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Binary)) return false;
		Binary b = (Binary) left;
		return b.left.isEqual(left) && b.right.isEqual(right) && b.op == op;
	}

	public void setTarget(Label ifFalse) {
		switch (op) {
			case logic_and: case logic_or:
			case lss: case leq:
			case gtr: case geq:
			case equ: case neq: case feq:
				this.target = ifFalse;
				break;

			default:
				if (ifFalse != null) throw new IllegalArgumentException("Operator not support: " + byId(op));
		}
	}
}
