package roj.compiler.runtime;

/**
 * @author Roj234
 * @since 2025/07/28 18:26
 */
@FunctionalInterface
public interface f {
	ReturnStack<?> call(ReturnStack<?> arguments);
}
