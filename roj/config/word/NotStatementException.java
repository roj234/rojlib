package roj.config.word;

/**
 * @author Roj234
 * @since 2021/4/29 22:10
 */
public class NotStatementException extends RuntimeException {
	static final long serialVersionUID = 0L;

	public NotStatementException() {
		super("This operator does not support no-return environment");
	}
}
