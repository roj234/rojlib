package roj.plugins.obfuscator.raw;

import roj.asmx.Context;
import roj.plugins.obfuscator.ObfuscateTask;

import java.util.Collections;
import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 14:14
 */
class RandomOrder implements ObfuscateTask {
	@Override
	public void apply(Context ctx, Random rand) {
		var cn = ctx.getData();

		Collections.shuffle(cn.fields, rand);
		Collections.shuffle(cn.methods, rand);
	}
}
