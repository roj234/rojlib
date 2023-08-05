package roj.lavac.block;

import roj.collect.RingBuffer;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2023/11/4 0004 0:08
 */
public interface Node {
	default void toString(CharList sb, int depth) { sb.append(toString()); }
	default Node compress() { return this; }
	default void free(RingBuffer<Node>[] buf) {}
}
