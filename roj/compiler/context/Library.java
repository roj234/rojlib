package roj.compiler.context;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.IClass;

import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:51
 */
public interface Library {
	@Nullable
	default Set<String> content() { return null; }
	IClass get(CharSequence name);
	default void close() throws Exception {}
}