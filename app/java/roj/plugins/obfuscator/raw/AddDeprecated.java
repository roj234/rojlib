package roj.plugins.obfuscator.raw;

import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.attr.UnparsedAttribute;
import roj.asmx.Context;
import roj.plugins.obfuscator.ObfuscateTask;
import roj.util.ByteList;

import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 14:21
 */
class AddDeprecated implements ObfuscateTask {
	static final UnparsedAttribute DEPRECATED = new UnparsedAttribute("Deprecated", ByteList.EMPTY);

	@Override
	public void apply(Context ctx, Random rand) {
		var cn = ctx.getData();

		for (MethodNode mn : cn.methods) mn.addAttribute(DEPRECATED);
		for (FieldNode fn : cn.fields) fn.addAttribute(DEPRECATED);
	}
}
