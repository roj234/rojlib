package roj.util.function;

import roj.ci.annotation.AliasOf;

/**
 * @author Roj234
 * @since 2024/11/21 16:30
 */
@FunctionalInterface
@AliasOf(Runnable.class)
public interface ExceptionalRunnable<EX extends Throwable> {
	void run() throws EX;
}
