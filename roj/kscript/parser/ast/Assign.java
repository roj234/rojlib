package roj.kscript.parser.ast;

import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.parser.JSLexer;
import roj.kscript.type.KDouble;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符-赋值
 *
 * @author Roj233
 * @since 2020/10/15 13:01
 */
public final class Assign implements Expression {
	LoadExpression left;
	Expression right;

	public Assign(LoadExpression left, Expression right) {
		this.left = left;
		this.right = right;
	}

	@Nonnull
	@Override
	public Expression compress() {
		left = (LoadExpression) left.compress();
		right = right.compress();
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(KS_ASM tree, boolean noRet) {
		boolean var = left instanceof Variable;
		boolean opDone = false;

		if (right instanceof Binary) {
			Binary bin = (Binary) right;

			Expression al = null, ar = null;
			if (bin.left.isEqual(left)) {
				al = bin.left;
				ar = bin.right;
			} else if (bin.right.isEqual(left)) {
				al = bin.right;
				ar = bin.left;
			}

			if (al != null) {
				switch (bin.operator) {
					case JSLexer.logic_or:
					case JSLexer.logic_and:
						break;
					case JSLexer.add:
					case JSLexer.dec:
						// i = i + 1 or i += 1; but not i++
						if (ar.isConstant() && ar.type() == INT || ar.type() == DOUBLE) {
							double i = ar.asCst().asDouble();

							double count = bin.operator == JSLexer.add ? i : -i;

							if (var) {
								if (((int) count) != count) break;
								Variable v = (Variable) this.left;
								tree.Inc(v.name, (int) count);
								v._after_write_op();
								if (!noRet) {
									tree.Get(v.name);
								}
							} else {
								left.writeLoad(tree);

								tree.Std(Opcode.DUP2).Std(Opcode.GET_OBJ).Load(KDouble.valueOf(count)).Std(bin.operator == JSLexer.add ? Opcode.ADD : Opcode.SUB);
								if (!noRet) {
									tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
								}
								tree.Std(Opcode.PUT_OBJ);
							}

							opDone = true;
							break;
						}
					default:
						// etc. k = k * 3;
						if (!var) {
							left.writeLoad(tree);

							ar.write(tree.Std(Opcode.DUP2).Std(Opcode.GET_OBJ), false);
							bin.writeOperator(tree);

							if (!noRet) {
								tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
							}

							tree.Std(Opcode.PUT_OBJ);

							opDone = true;
						}
						break;
				}
			}
		}

		if (!opDone) {
			if (var) {
				right.write(tree, false);
				Variable v = (Variable) this.left;
				tree.Set(v.name);
				v._after_write_op();
				if (!noRet) tree.Get(v.name);
			} else {
				// parent name value
				left.writeLoad(tree);
				right.write(tree, false);
				if (!noRet) {
					tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
				}
				tree.Std(Opcode.PUT_OBJ);
			}
		}
	}

	@Override
	public byte type() {
		return left.type();
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Assign)) return false;
		Assign assign = (Assign) left;
		return assign.left.isEqual(left) && assign.right.isEqual(right);
	}

	@Override
	public KType compute(Map<String, KType> param) {
		final KType result = right.compute(param);
		if (left instanceof Variable) {
			Variable v = (Variable) left;
			param.put(v.name, result);
		} else {
			left.assignInCompute(param, result);
		}
		return result;
	}

	@Override
	public String toString() {
		return left.toString() + '=' + right.toString();
	}
}
