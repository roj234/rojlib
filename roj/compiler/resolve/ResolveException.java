package roj.compiler.resolve;

/**
 * @author Roj234
 * @since 2024/1/23 0023 6:12
 */
public class ResolveException extends RuntimeException {
	public ResolveException(String message) { super(message); }
	public ResolveException(String message, Throwable cause) { super(message, cause); }

	protected ResolveException(String message, Throwable cause,
						boolean enableSuppression,
						boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}