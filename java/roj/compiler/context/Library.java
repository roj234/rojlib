package roj.compiler.context;

import roj.asm.tree.IClass;

import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:51
 */
public interface Library {
	//TODO module
	default String getModule(String className) { return null; }

	Set<String> content();
	IClass get(CharSequence name);
	default void close() throws Exception {}
}