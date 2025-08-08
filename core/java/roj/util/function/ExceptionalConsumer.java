package roj.util.function;

import roj.ci.annotation.AliasOf;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/05/21 4:48
 */
@FunctionalInterface
@AliasOf(Consumer.class)
public interface ExceptionalConsumer<T, EX extends Throwable> {
	void accept(T value) throws EX;
}