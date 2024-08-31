package roj.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author Roj233
 * @since 2022/1/24 11:55
 */
interface Selectable {
	default void tick(int elapsed) throws Exception {}

	default boolean isClosedOn(SelectionKey key) {return !key.isValid();}

	default void close() throws Exception {}

	void selected(int readyOps) throws Exception;

	void register(Selector sel, int ops, Object att) throws IOException;

	default Boolean exceptionCaught(String stage, Throwable ex) throws Exception {return null;}
}