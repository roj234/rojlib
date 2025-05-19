package roj.concurrent;

import roj.compiler.api.ExceptionalStub;

import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2025/05/21 4:48
 */
@FunctionalInterface
@ExceptionalStub(Supplier.class)
public interface ExceptionalConsumer<T, EX extends Throwable> {
	void accept(T value) throws EX;
}