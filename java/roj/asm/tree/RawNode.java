package roj.asm.tree;

/**
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public interface RawNode extends Attributed {
	default String ownerClass() { throw new UnsupportedOperationException(getClass().getName()); }

	String name();
	default void name(String name) { throw new UnsupportedOperationException(getClass().getName()); }

	String rawDesc();
	default void rawDesc(String rawDesc) { throw new UnsupportedOperationException(getClass().getName()); }
}