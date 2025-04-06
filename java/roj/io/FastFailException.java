package roj.io;

/**
 * @author Roj233
 * @since 2022/6/2 20:54
 */
public final class FastFailException extends RuntimeException {
	public Object payload;

	public FastFailException(String message) {super(message);}
	@Deprecated
	public FastFailException(long time) {super(time+"ms的等待已超时");}
	public FastFailException(String message, Throwable cause) {super(message,cause);}
	public FastFailException(String message, Object payload) {super(message);this.payload = payload;}

	@Override
	public Throwable fillInStackTrace() {return this;}
}