package roj.io;

/**
 * @author Roj233
 * @since 2022/6/2 20:54
 */
public final class FastFailException extends RuntimeException {
	public FastFailException(String msg) {
		super(msg);
	}

	public FastFailException(long time) {
		super("Timeout after " + time + "ms");
	}

	public FastFailException(String s, Throwable e) {
		super(s,e);
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
