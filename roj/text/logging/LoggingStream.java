package roj.text.logging;

import roj.ui.DelegatedPrintStream;

/**
 * @author Roj233
 * @since 2022/1/13 0:48
 */
public class LoggingStream extends DelegatedPrintStream {
	private final Logger logger;

	@Deprecated
	public LoggingStream() {
		this(Logger.getLogger("STDOUT"));
	}

	public LoggingStream(Logger logger) {
		super(2000);
		this.logger = logger;
	}

	public Logger getLogger() {
		return logger;
	}

	@Override
	protected void newLine() {
		logger.log(Level.INFO, sb, null);
		sb.clear();
	}
}
