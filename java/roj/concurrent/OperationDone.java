package roj.concurrent;

import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2020/8/23 0:54
 */
public final class OperationDone extends RuntimeException {
	public static final RuntimeException NEVER = Helpers.nonnull();
	public static final OperationDone INSTANCE = new OperationDone("操作完成.");

	private OperationDone(String s) {super(s);}

	// backtrace will strong reference some class, weird……
	@Override
	public synchronized Throwable fillInStackTrace() {return this;}
}