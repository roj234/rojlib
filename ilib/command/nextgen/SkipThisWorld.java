package ilib.command.nextgen;

/**
 * @author Roj234
 * @since 2023/3/1 0001 0:43
 */
public class SkipThisWorld extends RuntimeException {
	public static final SkipThisWorld INSTANCE = new SkipThisWorld();
	public Throwable fillInStackTrace() {
		return this;
	}
}
