package roj.kscript.parser.ast;

import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2021/6/18 9:52
 */
public final class MethodSpreaded extends Method {
	public MethodSpreaded(Method source) {
		this.func = source.func;
		this.args = source.args;
		this.flag = source.flag;
		if (args.size() > 64) throw new UnsupportedOperationException("带有spread操作符的方法调用的参数超过了64个，我不愿意支持，你去弄吧，蟹蟹");
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		this.func.write(tree, false);

		long activeBits = 0L;
		for (int i = 0; i < args.size(); i++) {
			Expression expr = args.get(i);
			expr.write(tree, false);
			if (expr instanceof Spread) {
				activeBits |= (1L << i);
			}
		}

		if ((flag & 1) != 0) {
			tree.NewSpread(args.size(), noRet, activeBits);
		} else {
			tree.InvokeSpread(args.size(), noRet, activeBits);
		}
	}

	@Nonnull
	@Override
	public Expression compress() {
		return this;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		if (true) throw new UnsupportedOperationException("懒得做");

		List<KType> vals = new ArrayList<>(args.size());
		for (int i = 0; i < args.size(); i++) {
			vals.add(args.get(i).compute(param));
		}

		return null;
	}

	@Override
	public byte type() {
		return -1;
	}
}
