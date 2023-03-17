package roj.kscript.parser.ast;

import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KBool;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/3 20:14
 */
public final class AsBool implements Expression {
	Expression right;

	public AsBool(Expression right) {
		this.right = right;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		tree.Std(Opcode.CAST_BOOL);
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return KBool.valueOf(right.compute(param).asBool());
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		if (noRet) throw new NotStatementException();

		ctx.list.add(NodeCache.a_asBool_0());
		ctx.list.add(NodeCache.a_asBool_1());
	}

	@Override
	public byte type() {
		return 3;
	}
}
