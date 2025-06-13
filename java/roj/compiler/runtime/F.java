package roj.compiler.runtime;

/**
 * @author Roj234
 * @since 2025/07/28 18:26
 */
@FunctionalInterface
public interface F {
	ReturnStack<?> call(ReturnStack<?> arguments);
}
