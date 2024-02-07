package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.type.IType;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.api.ASM;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
final class Assign extends ExprNode {
	private VarNode left;
	private ExprNode right;
	private TypeCast.Cast cast;

	Assign(VarNode left, ExprNode right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public String toString() { return left+" = "+right; }

	// javac is left.type() so you can't use
	// int a = xxx.yyy = 3;
	// when xxx.yyy is Object or similar field
	//
	// I fixed this annoying 'feature'
	//
	// however this have introduced new problem if you are using:
	// Double a,b,c;
	// a = b = c = 3;
	// This will create 3 different Double objects
	@Override
	public IType type() { return right.type(); }

	@Override
	public ExprNode resolve(CompileContext ctx) {
		ExprNode node = left.resolve(ctx);
		if (node instanceof VarNode vn && !vn.isFinal()) left = vn;
		else ctx.report(Kind.ERROR, "assign.error.final", left);

		ExprNode prev = right;
		right = prev.resolve(ctx);

		IType lType = left.type();
		cast = ctx.castTo(right.type(), lType, TypeCast.getDataCap(lType.getActualType()) < 4 ? TypeCast.E_NUMBER_DOWNCAST : 0);

		if (prev.getClass() == Cast.class) {
			int castType = ((Cast) prev).cast.type;
			if ((castType == -1 || castType == -2) && !(left instanceof LocalVariable))
				ctx.report(Kind.NOTE, "assign.incompatible.redundantCast");
		}

		// 也许搞个 未修改的常量变量 => 常量
		if (right.isConstant() && left instanceof LocalVariable lv) {
			//ctx.setConstantValue(lv, right.constVal());
		}

		return this;
	}

	static void incOrDec(VarNode expr, MethodWriter cw, boolean noRet, boolean returnBefore, int amount) {
		boolean isLv = false;
		int dtype = TypeCast.getDataCap(expr.type().getActualType());
		// == 4 (int) 防止byte自增导致溢出什么的...
		if (expr instanceof LocalVariable lv) {
			isLv = true;

			if (dtype == 4 && (short)amount == amount) {
				if (!noRet & returnBefore) cw.load(lv.v);
				cw.iinc(lv.v, amount);
				if (!noRet & !returnBefore) cw.load(lv.v);
				return;
			}
		}

		expr.preLoadStore(cw);

		int op;
		if (amount < 0 && amount != Integer.MIN_VALUE) {
			amount = -amount;
			op = Opcodes.ISUB-4;
		} else {
			op = Opcodes.IADD-4;
		}

		int type2 = Math.max(4, dtype);
		switch (type2) {
			case 4: cw.ldc(amount); break;
			case 5: cw.ldc((long)amount); break;
			case 6: cw.ldc((float)amount); break;
			case 7: cw.ldc((double)amount); break;
		}
		cw.one((byte) (op + type2));
		if (dtype < 4 && isLv) cw.one((byte) (Opcodes.I2B-1 + dtype));

		expr.postStore(cw);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		// a = a + 1
		if (right instanceof Binary br) {
			if (br.left.equals(left)) {
				if (sameVarShrink(cw, br, noRet, br.right)) return;
			} else if (br.left.isConstant() && br.right.equals(left)) {
				if (sameVarShrink(cw, br, noRet, br.left)) return;
			}
		} else {
			// a = b = c = d
			if (right instanceof Assign ass) {
				Assign tmpVar = null;

				List<Assign> assigns = new SimpleList<>();
				assigns.add(this);

				while (ass.left.canBeReordered()) {
					if (ass.left instanceof LocalVariable) tmpVar = ass;
					assigns.add(ass);

					if (!(ass.right instanceof Assign r)) break;
					ass = r;
				}

				checkSimpleLdc:
				if (ass.right.isConstant()) {
					int cap = TypeCast.getDataCap(ass.right.type().getActualType());
					if (cap > 4 || cap == 0) break checkSimpleLdc;
					int value = ((AnnVal) ass.right.constVal()).asInt();
					if (value < -1 || value > 5) break checkSimpleLdc;

					for (int i = 0; i < assigns.size(); i++) {
						Assign node = assigns.get(i);

						node.left.preStore(cw);
						if (node.isCastNeeded()) ass.right.writeDyn(cw, node.cast);
						else ass.right.write(cw, false);

						node.left.postStore(cw);
					}
					if (!noRet) ass.right.write(cw, false);

					return;
				}

				if (tmpVar == null) {
					// ...
				} else {
					if (tmpVar.isCastNeeded()) ass.right.writeDyn(cw, tmpVar.cast);
					else ass.right.write(cw, false);

					tmpVar.left.postStore(cw);

					for (int i = 0; i < assigns.size(); i++) {
						Assign node = assigns.get(i);
						if (node == tmpVar) continue;

						node.left.preStore(cw);
						if (node.isCastNeeded()) tmpVar.left.writeDyn(cw, node.cast);
						else tmpVar.left.write(cw, false);
						node.left.postStore(cw);
					}
					if (!noRet) tmpVar.left.write(cw, false);

					return;
				}
			}

			left.preStore(cw);
			right.write(cw, false);
		}

		// 测试一下
		if (!noRet) left.copyValue(cw, ASM.i2z(right.type().rawType().length()-1));

		if (isCastNeeded()) cast.write(cw);
		left.postStore(cw);
	}

	private boolean isCastNeeded() { return (cast.type != -1 && cast.type != -2) || left.getClass() == LocalVariable.class; }

	private boolean sameVarShrink(MethodWriter cw, Binary br, boolean noRet, ExprNode operand) {
		// to IINC if applicable
		block:
		if (left instanceof LocalVariable lv && TypeCast.getDataCap(br.type().getActualType()) == 4) {
			int value = ((AnnValInt) operand.constVal()).value;

			switch (br.operator) {
				default: break block;
				case JavaLexer.add: break;
				case JavaLexer.sub: value = -value; break;
			}
			if ((short)value != value) break block;

			cw.iinc(lv.v, value);
			if (!noRet) left.write(cw, false);
			return true;
		}

		left.preLoadStore(cw);
		if (operand == br.left) br.writeLeft(cw);
		else br.writeRight(cw);
		br.writeOperator(cw);

		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Assign)) return false;
		Assign assign = (Assign) o;
		return assign.left.equals(left) && assign.right.equals(right);
	}

	@Override
	public int hashCode() {
		int result = left.hashCode();
		result = 31 * result + right.hashCode();
		return result;
	}
}