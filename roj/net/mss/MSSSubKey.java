package roj.net.mss;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * @author solo6975
 * @since 2022/2/14 0:49
 */
public interface MSSSubKey {
    /**
     * Initialize as Sender
     */
    void initA(Random r, int sharedRandom);
    /**
     * Initialize as Receiver
     */
    default void initB(Random r, int sharedRandom) { initA(r, sharedRandom); }

    int length();
    void clear();

    void writeA(ByteBuffer bb);
    byte[] readA(ByteBuffer bb);
    default void writeB(ByteBuffer bb) { writeA(bb); }
    default byte[] readB(ByteBuffer bb) { return readA(bb); }
}
