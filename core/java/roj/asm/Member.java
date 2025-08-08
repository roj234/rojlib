package roj.asm;

/**
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public interface Member extends Attributed {
	default String owner() { throw new UnsupportedOperationException(getClass().getName()); }

	String name();
	default void name(String name) { throw new UnsupportedOperationException(getClass().getName()); }

	String rawDesc();
	default void rawDesc(String rawDesc) { throw new UnsupportedOperationException(getClass().getName()); }
}