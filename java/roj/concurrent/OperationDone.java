package roj.concurrent;

import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2020/8/23 0:54
 */
public final class OperationDone extends RuntimeException {
	static final long serialVersionUID = 2333L;

	public static final OperationDone NEVER = Helpers.nonnull();
	public static final OperationDone INSTANCE = new OperationDone("Operation done.");

	private OperationDone(String s) {
		super(s);
	}
}
