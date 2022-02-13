package roj.net.mss;

import roj.util.ByteList;

import java.math.BigInteger;
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
    void init(Random r, boolean def);
    /**
     * Initialize as Receiver
     */
    void init(Random r);

    int length();
    void reset();

    boolean write1(ByteBuffer bb);
    BigInteger read1(ByteBuffer bb);
    boolean write2(ByteBuffer bb);
    BigInteger read2(ByteBuffer bb);

    void write1(ByteList bb);
    BigInteger read1(ByteList bb);
    void write2(ByteList bb);
    BigInteger read2(ByteList bb);
}
