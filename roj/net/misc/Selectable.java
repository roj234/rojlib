package roj.net.misc;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * @author Roj233
 * @since 2022/1/24 11:55
 */
public interface Selectable extends Closeable {
    default void tick(int elapsed) throws IOException {}

    default boolean isClosedOn(SelectionKey key) {
        return !key.isValid();
    }
    default void close() throws IOException {}

    void selected(int readyOps) throws Exception;
}
