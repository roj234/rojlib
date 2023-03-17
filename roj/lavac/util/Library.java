package roj.lavac.util;

import roj.asm.tree.IClass;

import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:51
 */
public interface Library {
	Set<String> content();

	boolean has(CharSequence name);

	IClass get(CharSequence name);

	void close() throws Exception;
}
