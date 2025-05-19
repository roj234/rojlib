package roj.concurrent;

import roj.compiler.api.ExceptionalStub;

import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2025/06/03 9:18
 */
@FunctionalInterface
@ExceptionalStub(Supplier.class)
public interface ExceptionalSupplier<T, EX extends Throwable> {
	T get() throws EX;
}
