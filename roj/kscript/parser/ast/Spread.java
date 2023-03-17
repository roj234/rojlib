package roj.kscript.parser.ast;

import roj.config.word.NotStatementException;
import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KArray;
import roj.kscript.type.KType;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 扩展运算符
 *
 * @author solo6975
 * @since 2021/6/16 20:11
 */
public final class Spread implements Expression {
	Expression provider;

	public Spread(Expression provider) {
		this.provider = provider;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) throws NotStatementException {
		provider.write(tree, false);
		//tree.Std(Opcode.SPREAD);
	}

	@Nonnull
	@Override
	public Expression compress() {
		if (provider.isConstant()) {
			return Constant.valueOf(__spread(provider.asCst().val()));
		}
		return this;
	}

	private static IArray __spread(KType val) {
		if (val.canCastTo(Type.ARRAY)) {
			return val.asArray();
		} else {
			IObject obj = val.asObject();
			int l = obj.get("length").asInt();
			KArray array = new KArray(l);
			for (int i = 0; i < l; i++) {
				array.add(obj.get(String.valueOf(i)));
			}
			return array;
		}
	}

	@Override
	public byte type() {
		return -1;
	}

	@Override
	public boolean isConstant() {
		return provider.isConstant();
	}

	@Override
	public Constant asCst() {
		return Constant.valueOf(__spread(provider.asCst().val()));
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return __spread(provider.compute(param));
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Spread)) return false;
		Spread sp = (Spread) left;
		return sp.provider.isEqual(provider);
	}

	@Override
	public String toString() {
		return "... " + provider;
	}
}
