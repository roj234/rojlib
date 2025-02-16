package roj.plugins.obfuscator;

import roj.asm.util.Context;
import roj.concurrent.TaskHandler;
import roj.crypt.MT19937;

import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 0018 1:04
 */
public interface ObfuscateTask {
	default boolean isMulti() {return false;}
	default void forEach(List<Context> contextList, MT19937 rand, TaskHandler executor) {
		for (int i = 0; i < contextList.size(); i++) {
			apply(contextList.get(i), rand);
		}
	}
	void apply(Context ctx, Random rand);
}
