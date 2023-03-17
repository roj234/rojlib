package roj.kscript.parser.ast;

import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.kscript.asm.IfNode;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.LabelNode;
import roj.kscript.asm.Opcode;
import roj.kscript.parser.JSLexer;
import roj.kscript.type.*;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Binary implements Expression {
	final short operator;
	Expression left, right;
	LabelNode target;

	public Binary(short operator, Expression left, Expression right) {
		switch (operator) {
			default:
				throw new IllegalArgumentException("Unsupported operator " + JSLexer.byId(operator));
			case JSLexer.pow:
			case JSLexer.add:
			case JSLexer.and:
			case JSLexer.div:
			case JSLexer.lsh:
			case JSLexer.mod:
			case JSLexer.mul:
			case JSLexer.or:
			case JSLexer.rsh:
			case JSLexer.rsh_unsigned:
			case JSLexer.sub:
			case JSLexer.xor:
			case JSLexer.logic_and:
			case JSLexer.logic_or:
			case JSLexer.lss:
			case JSLexer.gtr:
			case JSLexer.geq:
			case JSLexer.leq:
			case JSLexer.equ:
			case JSLexer.neq:
			case JSLexer.feq:
				break;
		}
		this.operator = operator;
		this.left = left;
		this.right = right;
	}

	public int constNum() {
		int a = (left = left.compress()).isConstant() ? 1 : 0;
		if ((right = right.compress()).isConstant()) a++;
		return a;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		if (noRet) {
			switch (operator) {
				case JSLexer.logic_or:
				case JSLexer.logic_and:
					break;
				default:
					throw new NotStatementException();
			}
		}

		switch (operator) {
			case JSLexer.logic_or:
			case JSLexer.logic_and:
				break;
			default:
				left.write(tree, false);
				right.write(tree, false);
		}

		writeOperator(tree);
	}

	void writeOperator(KS_ASM tree) {
		switch (operator) {
			case JSLexer.lss:
			case JSLexer.gtr:
			case JSLexer.geq:
			case JSLexer.leq:
			case JSLexer.feq:
			case JSLexer.equ:
			case JSLexer.neq:
				if (target == null) {
					tree.IfLoad(operator);
				} else {
					tree.If(target, operator);
				}
				break;
			case JSLexer.add:
				tree.Std(Opcode.ADD);
				break;
			case JSLexer.and:
				tree.Std(Opcode.AND);
				break;
			case JSLexer.logic_and: {
				// if a && b

				left.write(tree, false);
				if (target != null) {
					right.write(tree.If(target, IfNode.TRUE), false);
					tree.If(target, IfNode.TRUE);
				} else {
					LabelNode falseTo = new LabelNode();
					LabelNode fin = new LabelNode();
					right.write(tree.If(falseTo, IfNode.TRUE), false);
					tree.If(falseTo, IfNode.TRUE).Load(KBool.TRUE).Goto(fin).Node(falseTo).Load(KBool.FALSE).node0(fin);
				}
			}
			break;
			case JSLexer.logic_or: {
				LabelNode falseTo = new LabelNode();
				LabelNode fin = new LabelNode();

				// if a || b
				// = a ? a : b 而不是true/false ...

				left.write(tree, false);
				if (target != null) {
					right.write(tree.If(falseTo, IfNode.TRUE).Goto(fin).Node(falseTo), false);
					tree.If(target, IfNode.TRUE).node0(fin);
				} else {
					right.write(tree.Std(Opcode.DUP)
									// left, left
									.If(falseTo, IfNode.TRUE).Goto(fin).Node(falseTo).Std(Opcode.POP), false);
					tree.node0(fin);
				}
			}
			break;
			case JSLexer.or:
				tree.Std(Opcode.OR);
				break;
			case JSLexer.div:
				tree.Std(Opcode.DIV);
				break;
			case JSLexer.lsh:
				tree.Std(Opcode.SHIFT_L);
				break;
			case JSLexer.mod:
				tree.Std(Opcode.MOD);
				break;
			case JSLexer.mul:
				tree.Std(Opcode.MUL);
				break;
			case JSLexer.rsh:
				tree.Std(Opcode.SHIFT_R);
				break;
			case JSLexer.rsh_unsigned:
				tree.Std(Opcode.U_SHIFT_R);
				break;
			case JSLexer.sub:
				tree.Std(Opcode.SUB);
				break;
			case JSLexer.xor:
				tree.Std(Opcode.XOR);
				break;
			case JSLexer.pow:
				tree.Std(Opcode.POW);
				break;
		}
	}

	@Override
	public boolean isConstant() {
		return left.isConstant() && right.isConstant();
	}

	@Nonnull
	@Override
	public Expression compress() {
		if ((left = left.compress()).isConstant() != (right = right.compress()).isConstant() || !right.isConstant()) {
			switch (operator) {
				case JSLexer.logic_and:
					if (left.isConstant()) {
						if (left.asCst().asBool()) {
							// temporary ifload node
							return new AsBool(right);
						} else {
							return Constant.valueOf(false);
						}
					}
					break;
				case JSLexer.logic_or:
					if (left.isConstant()) {
						if (left.asCst().asBool()) {
							return left.asCst();
						} else {
							return right;
						}
					}
			}
			return this;
		}

		final boolean d = left.type() == 1 || right.type() == 1;
		final Constant l = left.compress().asCst(), r = right.compress().asCst();

		switch (operator) {
			case JSLexer.pow:
				return Constant.valueOf(Math.pow(l.asDouble(), r.asDouble()));
			case JSLexer.lss:
				return Constant.valueOf(d ? l.asDouble() < r.asDouble() : l.asInt() < r.asInt());
			case JSLexer.gtr:
				return Constant.valueOf(d ? l.asDouble() > r.asDouble() : l.asInt() > r.asInt());
			case JSLexer.geq:
				return Constant.valueOf(d ? l.asDouble() >= r.asDouble() : l.asInt() >= r.asInt());
			case JSLexer.leq:
				return Constant.valueOf(d ? l.asDouble() <= r.asDouble() : l.asInt() <= r.asInt());
			case JSLexer.equ:
			case JSLexer.neq:
				return Constant.valueOf((operator == JSLexer.equ) == l.val().equalsTo(r.val()));
			case JSLexer.feq:
				KType lv = l.val();
				KType rv = r.val();
				switch (l.type()) {
					case 0:
					case 1:
						return Constant.valueOf(r.type() < 2 && lv.equalsTo(rv));
					case 2:
						return Constant.valueOf(r.type() == 2 && l.asString().equals(r.asString()));
					case 3:
						return Constant.valueOf(lv == rv);
				}
				throw OperationDone.NEVER;
			case JSLexer.add:
				switch (l.type()) {
					case 0:
					case 1:
						return r.type() == 2 ? Constant.valueOf(l.asString() + r.asString()) : Constant.valueOf(d ? KDouble.valueOf(l.asDouble() + r.asDouble()) : KInt.valueOf(l.asInt() + r.asInt()));
					case 2:
						return Constant.valueOf(l.asString() + r.asString());
					case 3:
						return Constant.valueOf(l.asInt() + r.asInt());
				}
				throw OperationDone.NEVER;
			case JSLexer.sub:
				return Constant.valueOf(d ? KDouble.valueOf(l.asDouble() - r.asDouble()) : KInt.valueOf(l.asInt() - r.asInt()));
			case JSLexer.div:
				return Constant.valueOf(l.asDouble() / r.asDouble());
			case JSLexer.mul:
				return Constant.valueOf(d ? KDouble.valueOf(l.asDouble() * r.asDouble()) : KInt.valueOf(l.asInt() * r.asInt()));
			case JSLexer.logic_and:
				return Constant.valueOf(l.asBool() && r.asBool());
			case JSLexer.logic_or:
				return l.asBool() ? l : r;
			case JSLexer.mod:
				return Constant.valueOf(KInt.valueOf(d ? (int) (l.asDouble() % r.asDouble()) : l.asInt() % r.asInt()));
			case JSLexer.and:
				return Constant.valueOf(l.asInt() & r.asInt());
			case JSLexer.lsh:
				return Constant.valueOf(l.asInt() << r.asInt());
			case JSLexer.or:
				return Constant.valueOf(l.asInt() | r.asInt());
			case JSLexer.rsh:
				return Constant.valueOf(l.asInt() >> r.asInt());
			case JSLexer.rsh_unsigned:
				return Constant.valueOf(l.asInt() >>> r.asInt());
			case JSLexer.xor:
				return Constant.valueOf(l.asInt() ^ r.asInt());
		}
		throw OperationDone.NEVER;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		KType l = left.compute(param);

		switch (operator) {
			case JSLexer.logic_and:
				return KBool.valueOf(l.asBool() && right.compute(param).asBool());
			case JSLexer.logic_or:
				return l.asBool() ? l : right.compute(param); // js就是这么干的
		}

		KType r = right.compute(param);
		boolean d = l.getType() == Type.DOUBLE || r.getType() == Type.DOUBLE;

		switch (operator) {
			case JSLexer.pow:
				return KDouble.valueOf(Math.pow(l.asDouble(), r.asDouble()));
			case JSLexer.lss:
				return KBool.valueOf(d ? l.asDouble() < r.asDouble() : l.asInt() < r.asInt());
			case JSLexer.gtr:
				return KBool.valueOf(d ? l.asDouble() > r.asDouble() : l.asInt() > r.asInt());
			case JSLexer.geq:
				return KBool.valueOf(d ? l.asDouble() >= r.asDouble() : l.asInt() >= r.asInt());
			case JSLexer.leq:
				return KBool.valueOf(d ? l.asDouble() <= r.asDouble() : l.asInt() <= r.asInt());
			case JSLexer.equ:
			case JSLexer.neq:
				return KBool.valueOf((operator == JSLexer.equ) == l.equalsTo(r));
			case JSLexer.feq:
				return KBool.valueOf(l.getType() == r.getType() && l.equalsTo(r));
			case JSLexer.add:
				if (l.getType() == Type.STRING) {
					return KString.valueOf(l.asString() + r.asString());
				}
				return d ? KDouble.valueOf(l.asDouble() + r.asDouble()) : KInt.valueOf(l.asInt() + r.asInt());
			case JSLexer.sub:
				return d ? KDouble.valueOf(l.asDouble() - r.asDouble()) : KInt.valueOf(l.asInt() - r.asInt());
			case JSLexer.div:
				return KDouble.valueOf(l.asDouble() / r.asDouble());
			case JSLexer.mul:
				return d ? KDouble.valueOf(l.asDouble() * r.asDouble()) : KInt.valueOf(l.asInt() * r.asInt());
			case JSLexer.mod:
				return KInt.valueOf(d ? (int) (l.asDouble() % r.asDouble()) : l.asInt() % r.asInt());
			case JSLexer.and:
				return KInt.valueOf(l.asInt() & r.asInt());
			case JSLexer.lsh:
				return KInt.valueOf(l.asInt() << r.asInt());
			case JSLexer.or:
				return KInt.valueOf(l.asInt() | r.asInt());
			case JSLexer.rsh:
				return KInt.valueOf(l.asInt() >> r.asInt());
			case JSLexer.rsh_unsigned:
				return KInt.valueOf(l.asInt() >>> r.asInt());
			case JSLexer.xor:
				return KInt.valueOf(l.asInt() ^ r.asInt());
		}
		throw OperationDone.NEVER;
	}

	@Override
	public byte type() {
		if (left.type() == -1 || right.type() == -1) return -1;

		switch (operator) {
			case JSLexer.lss:
			case JSLexer.gtr:
			case JSLexer.geq:
			case JSLexer.leq:
			case JSLexer.equ:
			case JSLexer.feq:
			case JSLexer.neq:
			case JSLexer.logic_and:
			case JSLexer.logic_or:
				return 3;
			case JSLexer.and:
			case JSLexer.or:
			case JSLexer.xor:
			case JSLexer.lsh:
			case JSLexer.rsh:
			case JSLexer.rsh_unsigned:
			case JSLexer.mod:
				return 0;
			case JSLexer.add:
			case JSLexer.div:
			case JSLexer.mul:
			case JSLexer.sub:
			case JSLexer.pow:
				return (byte) (right.type() == 1 || left.type() == 1 ? 1 : 0);
		}
		return -1;
	}

	@Override
	public String toString() {
		String l = left.toString();
		if (left.type() == -1) l = '(' + l + ')';
		String r = right.toString();
		if (right.type() == -1) r = '(' + r + ')';
		return l + JSLexer.byId(operator) + r;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Binary)) return false;
		Binary b = (Binary) left;
		return b.left.isEqual(left) && b.right.isEqual(right) && b.operator == operator;
	}

	public void setTarget(LabelNode ifFalse) {
		switch (operator) {
			case JSLexer.logic_and: // only those need jump
			case JSLexer.logic_or:
			case JSLexer.lss:
			case JSLexer.gtr:
			case JSLexer.geq:
			case JSLexer.leq:
			case JSLexer.equ:
			case JSLexer.neq:
			case JSLexer.feq:
				this.target = ifFalse;
				break;
			default:
				if (ifFalse != null) throw new IllegalArgumentException("Operator not support: " + JSLexer.byId(operator));
		}
	}
}
