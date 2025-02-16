package roj.plugins.obfuscator.raw;

import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.util.Context;
import roj.plugins.obfuscator.ObfuscateTask;

import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 0018 14:12
 */
class AllPublic implements ObfuscateTask {
	@Override
	public void apply(Context ctx, Random rand) {
		var cn = ctx.getData();
		cn.modifier &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL);
		cn.modifier |= Opcodes.ACC_PUBLIC;
		if ((cn.modifier & Opcodes.ACC_INTERFACE) == 0) {
			for (FieldNode field : cn.fields) {
				field.modifier &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL);
				field.modifier |= Opcodes.ACC_PUBLIC;
			}
			for (MethodNode method : cn.methods) {
				method.modifier &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL);
				method.modifier |= Opcodes.ACC_PUBLIC;
			}
		}
	}
}
