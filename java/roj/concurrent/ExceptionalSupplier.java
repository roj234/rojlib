package roj.concurrent;

import roj.plugins.ci.annotation.AliasOf;

import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2025/06/03 9:18
 */
@FunctionalInterface
@AliasOf(Supplier.class)
public interface ExceptionalSupplier<T, EX extends Throwable> {
	T get() throws EX;
}
