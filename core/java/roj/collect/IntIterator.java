package roj.collect;

import java.util.PrimitiveIterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface IntIterator extends PrimitiveIterator.OfInt {
	default void reset() {
		throw new UnsupportedOperationException();
	}
}
