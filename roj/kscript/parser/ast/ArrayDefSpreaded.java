package roj.kscript.parser.ast;

import roj.kscript.api.IArray;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KArray;
import roj.kscript.type.KType;

import java.util.List;
import java.util.Map;

/**
 * 操作符 - 定义数组 ...
 *
 * @author Roj233
 * @since 2021/6/18 8:50
 */
public final class ArrayDefSpreaded extends ArrayDef {
	Spread sp;

	public ArrayDefSpreaded(List<Expression> args) {
		sp = (Spread) args.remove(args.size() - 1);
		__init(args);
	}

	@Override
	public boolean isEqual(Expression left) {
		return this == left || (super.isEqual(left) && sp.isEqual(((ArrayDefSpreaded) left).sp));
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		super.write(tree, noRet);
		tree.Std(Opcode.SPREAD_ARRAY);
	}

	@Override
	public KType compute(Map<String, KType> param) {
		final KArray v = (KArray) array.copy();
		if (!expr.isEmpty()) {
			for (int i = 0; i < expr.size(); i++) {
				Expression exp = expr.get(i);
				if (exp != null) {
					v.set(i, exp.compute(param));
				}
			}
		}
		IArray array2 = sp.compute(param).asArray();
		for (int i = 0; i < array2.size(); i++) {
			v.add(array2.get(i));
		}
		return v;
	}

	@Override
	public String toString() {
		return "ArrayDef [Spreaded] {" + "expr=" + expr + ", array=" + array + '}';
	}
}
