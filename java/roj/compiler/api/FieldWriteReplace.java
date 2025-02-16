package roj.compiler.api;

import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.insn.CodeWriter;

/**
 * 被写入阶段的{@link roj.compiler.ast.expr.DotGet}调用，可以修改字段访问语句
 * 该属性不会序列化
 * 用于实现自动属性与Java11以下的private字段访问
 * @author Roj234
 * @since 2024/4/15 0015 10:35
 */
public abstract class FieldWriteReplace extends Attribute {
	public static final String NAME = "LavaFieldHook";

	@Override
	public final boolean isEmpty() {return true;}
	@Override
	public final String name() {return NAME;}

	// 默认就是什么都不做
	public void writeRead(CodeWriter cw, String owner, FieldNode fn) {
		var opcode = (fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
		cw.field(opcode, owner, fn.name(), fn.rawDesc());
	}
	// 能否写入由owner的ACC_FINAL决定
	public void writeWrite(CodeWriter cw, String owner, FieldNode fn) {
		var opcode = (fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
		cw.field(opcode, owner, fn.name(), fn.rawDesc());
	}
}