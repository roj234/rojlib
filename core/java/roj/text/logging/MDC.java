package roj.text.logging;

/**
 * @author Roj234
 * @since 2025/11/06 05:54
 */
public final class MDC {
	public static void set(String key, Object value) {
		LogWriter.LOCAL.get().variables.put(key, value);
	}
	public static void remove(String key) {
		LogWriter.LOCAL.get().variables.remove(key);
	}
}
