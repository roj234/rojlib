package roj.text.logging;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public class SimpleLogger extends Logger {
	public SimpleLogger(LogContext ctx) {
		this.ctx = ctx;
		this.level = Level.DEBUG;
	}
}
