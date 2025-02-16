package roj.plugins.obfuscator.raw;

import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.attr.AttrUnknown;
import roj.asm.util.Context;
import roj.plugins.obfuscator.ObfuscateTask;
import roj.util.ByteList;

import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 0018 14:21
 */
class AddDeprecated implements ObfuscateTask {
	static final AttrUnknown DEPRECATED = new AttrUnknown("Deprecated", ByteList.EMPTY);

	@Override
	public void apply(Context ctx, Random rand) {
		var cn = ctx.getData();

		for (MethodNode mn : cn.methods) mn.putAttr(DEPRECATED);
		for (FieldNode fn : cn.fields) fn.putAttr(DEPRECATED);
	}
}
