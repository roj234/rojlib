package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/3 20:14
 */
public final class AsInt implements Expression {
	Expression right;

	public AsInt(Expression right) {
		this.right = right;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		tree.Std(Opcode.CAST_INT);
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return KInt.valueOf(right.compute(param).asInt());
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		if (noRet) throw new NotStatementException();

		ctx.list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asInt", "()I"));
		// todo should be VM.allocI call
		ctx.list.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/type/KInt", "valueOf", "(I)Lroj/kscript/type/KInt;"));
	}

	@Override
	public byte type() {
		return 0;
	}
}
