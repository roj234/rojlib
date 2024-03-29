package roj.util;

/**
 * Indicates a JNI Error has occurred.
 *
 * @author Roj234
 * @since 2021/10/14 22:35
 */
public final class NativeException extends RuntimeException {
	private static final long serialVersionUID = 0L;
	public NativeException(String msg) { super(msg); }
}
