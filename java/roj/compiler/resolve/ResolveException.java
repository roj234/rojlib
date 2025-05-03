package roj.compiler.resolve;

import roj.text.TextUtil;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/1/23 6:12
 */
public class ResolveException extends RuntimeException {
	public ResolveException(String message) { super(message); }
	public ResolveException(String message, Throwable cause) { super(message, cause); }

	protected ResolveException(String message, Throwable cause,
						boolean enableSuppression,
						boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public static ResolveException ofInternalError(String message) {return new ResolveException(message);}
	public static ResolveException ofIllegalInput(String message) {return new ResolveException(message);}
	public static ResolveException ofIllegalInput(String message, Object... args) {
		return new ResolveException(message+":"+ TextUtil.join(Arrays.asList(args), ":"));
	}
}