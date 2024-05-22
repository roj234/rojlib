package roj.compiler.runtime;

/**
 * @author Roj234
 * @since 2024/6/10 0010 1:35
 */
public class QuickUtils {
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

	public static int legacyClassSwitch(Object o, Class<?>[] types) {
		for (int i = 0; i < types.length;) {
			if (types[i++].isInstance(o)) return i;
		}
		return 0;
	}
}