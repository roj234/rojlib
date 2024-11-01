package roj.compiler.runtime;

import roj.ReferenceByGeneratedClass;

/**
 * @author Roj234
 * @since 2024/6/10 0010 1:35
 */
public class QuickUtils {
	@ReferenceByGeneratedClass
	public static Throwable twr(Throwable local, AutoCloseable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception stack) {
				if (local == null) return stack;
				local.addSuppressed(stack);
			}
		}

		return local;
	}
}