package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.NPInsnNode;
import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.util.Helpers;

import java.util.Map;

/**
 * 操作符 - 简单操作 - 获取this - aload_0
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Std implements Expression {
	public static final Std STD1 = new Std(1), STD2 = new Std(2);

	final byte type;

	public Std(int type) {
		this.type = (byte) type;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		tree.Std(type == 1 ? Opcode.THIS : Opcode.ARGUMENTS);
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Std)) return false;

		Std std = (Std) left;
		return std.type == type;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		if (type == 1) {
			KType t = param.get("<this>");
			if (t != null) return t;
		}
		throw new UnsupportedOperationException("No internal variable available");
	}

	@Override
	public void mark_spec_op(ParseContext ctx, int op_type) {
		if (op_type == 2) {
			Helpers.athrow(ctx.getLexer().err("write_to_native_variable"));
		}
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		if (noRet) throw new NotStatementException();

		// $this, argList
		ctx.list.add(NPInsnNode.of(type == 1 ? Opcodes.ALOAD_1 : Opcodes.ALOAD_2));
	}

	@Override
	public String toString() {
		return type == 1 ? "this" : "arguments";
	}
}
