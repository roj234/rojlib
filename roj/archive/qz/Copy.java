package roj.archive.qz;

import java.io.InputStream;
import java.io.OutputStream;

public final class Copy extends QZCoder {
    public static final Copy INSTANCE = new Copy();
    Copy() {}

    QZCoder factory() { return this; }
    private static final byte[] ID = {0};
    byte[] id() { return ID; }

    public OutputStream encode(OutputStream out) { return out; }
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) { return in; }
}
