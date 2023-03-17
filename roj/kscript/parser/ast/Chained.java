package roj.kscript.parser.ast;

import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KType;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Chained implements Expression {
	public final List<Expression> par;

	public Chained() {
		par = new ArrayList<>();
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Chained)) return false;
		Chained method = (Chained) left;
		return ArrayDef.arrayEq(par, method.par);
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		int t;
		Expression last = par.get(t = par.size() - 1);
		for (int i = 0; i < t; i++) {
			par.get(i).toVMCode(ctx, true);
		}
		last.toVMCode(ctx, false);
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		int t;
		Expression last = par.get(t = par.size() - 1);
		for (int i = 0; i < t; i++) {
			par.get(i).write(tree, true);
		}
		last.write(tree, false);
	}

	@Nonnull
	@Override
	public Expression compress() {
		for (int i = 0; i < par.size(); i++) {
			par.set(i, par.get(i).compress());
		}
		return par.size() == 1 ? par.get(0) : this;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		for (int i = 0; i < par.size() - 1; i++) {
			par.get(i).compute(param);
		}
		return par.get(par.size() - 1).compute(param);
	}

	@Override
	public byte type() {
		return par.get(par.size() - 1).type();
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		for (int i = 0; i < par.size(); i++) {
			sb.append(par.get(i).toString()).append(", ");
		}
		sb.setLength(sb.length() - 2);
		return sb.toString();
	}

	public void append(Expression cur) {
		par.add(cur);
	}
}
