package roj.util.function;

import roj.ci.annotation.AliasOf;

import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/10/2 22:25
 */
@FunctionalInterface
@AliasOf(Function.class)
public interface ExceptionalFunction<T, R, EX extends Throwable> {
	R apply(T t) throws EX;
}
