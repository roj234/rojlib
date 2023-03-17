package roj.kscript.util;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/3 14:20
 */
public final class JavaException extends RuntimeException {
	public JavaException(String msg) {
		super(msg);
	}

	@Override
	public String toString() {
		return getMessage();
	}
}
