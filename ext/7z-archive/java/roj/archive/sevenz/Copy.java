package roj.archive.sevenz;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class Copy extends SevenZCodec {
    private static final byte[] ID = {0};
    public static final Copy INSTANCE = new Copy();

    public byte[] id() {return ID;}

    public OutputStream encode(OutputStream out) {return out;}
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) {return in;}
}