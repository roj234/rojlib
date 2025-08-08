package roj.plugins.obfuscator;

import roj.asmx.Context;
import roj.concurrent.Executor;

import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 1:04
 */
public interface ObfuscateTask {
	default boolean isMulti() {return false;}
	default void forEach(List<Context> contextList, Random rand, Executor executor) {
		for (int i = 0; i < contextList.size(); i++) {
			apply(contextList.get(i), rand);
		}
	}
	void apply(Context ctx, Random rand);
}
