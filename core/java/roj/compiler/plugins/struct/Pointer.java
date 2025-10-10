package roj.compiler.plugins.struct;

import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2025/10/26 04:14
 */
public interface Pointer<T> {
	T value();
	long address();

	static Pointer<?> fromAddress(long address) {return Helpers.maybeNull();}
}
