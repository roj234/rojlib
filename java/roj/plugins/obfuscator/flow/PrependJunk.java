package roj.plugins.obfuscator.flow;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.insn.InsnList;
import roj.asmx.Context;
import roj.plugins.obfuscator.ObfuscateTask;

import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 14:28
 */
public class PrependJunk implements ObfuscateTask {
	private static final String LOL = "lol";
	private static final String JUNK = "\n".repeat(1000);
	private final InsnList sample;
	private final float possibility;

	public PrependJunk(float possibility) {
		this.possibility = possibility;

		sample = new InsnList();

		for (int i = 0; i < 2; i++) {
			sample.ldc(JUNK);
			sample.ldc(LOL);
			sample.ldc(JUNK);
			sample.insn(Opcodes.POP);
			sample.insn(Opcodes.SWAP);
			sample.insn(Opcodes.POP);
			sample.ldc(LOL);
			sample.insn(Opcodes.POP2);
		}
	}

	@Override
	public void apply(Context ctx, Random rand) {
		if (rand.nextFloat() > possibility) return;
		ClassNode cn = ctx.getData();
		for (MethodNode method : cn.methods) {
			if (method.name().equals("<init>")) continue;

			var code = method.getAttribute(cn.cp, Attribute.Code);
			if (code != null) {
				code.instructions.replaceRange(0,0,sample,true);
			}
		}
	}
}
