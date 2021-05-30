package roj.net.tcp.util;

import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;

/**
 * These methods may seem unnecessary but they are
 * placeholders for the {@link SecureSocket}
 *
 * @author solo6975
 * @since 2020/11/29 15:33
 */
public interface WrappedSocket extends AutoCloseable {
    SocketChannel socket();

    /**
     * @return true if successful.
     */
    boolean handShake() throws IOException;

    int read() throws IOException;

    ByteList buffer();

    int write(ByteList src) throws IOException;

    default long write(InputStream src, long max) throws IOException {
        long wrote = 0;

        int got;
        while (max > Integer.MAX_VALUE) {
            got = write(src, Integer.MAX_VALUE);
            if (got < 1) {
                return wrote;
            }
            max -= got;
            wrote += got;
        }
        got = write(src, (int) max);

        return got > 0 ? got + wrote : wrote;
    }

    int write(InputStream src, int max) throws IOException;

    /**
     * Flush any pending data to the network if possible.
     *
     * @return true if successful.
     */
    boolean dataFlush() throws IOException;

    /**
     * Start any connection shutdown processing.
     *
     * @return true if successful, and the data has been flushed.
     */
    boolean shutdown() throws IOException;

    void close() throws IOException;
}
