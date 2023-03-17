package roj.archive.qz;

import roj.archive.qz.xz.LZMA2InputStream;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.MemoryLimitException;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class LZMA2 extends QZCoder {
    public LZMA2() { options = new LZMA2Options(); }
    public LZMA2(int level) { options = new LZMA2Options(level); }
    public LZMA2(LZMA2Options options) { this.options = options; }
    LZMA2(boolean unused) {}

    QZCoder factory() { return new LZMA2(true); }
    private static final byte[] ID = {33};
    byte[] id() { return ID; }

    private int dictSize;
    private LZMA2Options options;

    @Override
    public OutputStream encode(OutputStream out) throws IOException {
        return options.getOutputStream(out);
    }

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) throws IOException {
        int dictSize = getDictSize();

        int memoryUsage = LZMA2InputStream.getMemoryUsage(dictSize);
        if (memoryUsage > maxMemoryLimitInKb) throw new MemoryLimitException(memoryUsage, maxMemoryLimitInKb);

        return new LZMA2InputStream(in, dictSize);
    }

    @Override
    public String toString() {
        return "LZMA2:"+(31-Integer.numberOfLeadingZeros(getDictSize()));
    }

    private int getDictSize() {
        return options==null?this.dictSize:options.getDictSize();
    }

    @Override
    void writeOptions(DynByteBuf buf) {
        int dictSize = getDictSize();
        int dictPower = Integer.numberOfLeadingZeros(dictSize);
        int secondBit = (dictSize >>> (30-dictPower)) - 2;
        buf.put((byte) ((19 - dictPower) * 2 + secondBit));
    }

    @Override
    void readOptions(DynByteBuf buf, int length) throws IOException {
        if (length != 1) throw new IOException("Invalid LZMA2 option");
        int b = buf.readUnsignedByte();
        if ((b & ~0x3f) != 0) throw new IOException("Invalid LZMA2 option");
        if (b >= 40) throw new IOException("词典超过java的支持上限(2GB)");

        int size = (2 | (b & 0x1)) << (b / 2 + 11);
        this.dictSize = size;
        if (options != null)
            options.setDictSize(size);
    }
}
