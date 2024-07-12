package roj.archive.qz;

import roj.archive.qz.xz.LZMA2InputStream;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMA2ParallelReader;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class LZMA2 extends QZCoder {
    public static boolean experimental_async_decompress;

    public LZMA2() { options = new LZMA2Options(); }
    public LZMA2(int level) { options = new LZMA2Options(level); }
    public LZMA2(LZMA2Options options) { this.options = options; }
    LZMA2(boolean unused) {}

    QZCoder factory() {return new LZMA2(true);}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || LZMA2.class != o.getClass()) return false;
        LZMA2 lzma2 = (LZMA2) o;

        if (dictSize != lzma2.dictSize) return false;
		return options != null ? options.equals(lzma2.options) : lzma2.options == null;
	}
    @Override
    public int hashCode() {return getDictSize();}

    private static final byte[] ID = {33};
    byte[] id() { return ID; }

    private int dictSize;
    private LZMA2Options options;

    @Override
    public OutputStream encode(OutputStream out) throws IOException {return options.getOutputStream(out);}

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
        int dictSize = getDictSize();
        if (uncompressedSize < dictSize) {
            dictSize = uncompressedSize < LZMA2Options.DICT_SIZE_MIN ? LZMA2Options.DICT_SIZE_MIN : (int) uncompressedSize;
        }
        useMemory(memoryLimit, LZMA2InputStream.getMemoryUsage(dictSize));

        return experimental_async_decompress ? new LZMA2ParallelReader(in, dictSize) : new LZMA2InputStream(in, dictSize);
    }
    private int getDictSize() {return options==null?this.dictSize:options.getDictSize();}

    @Override
    public String toString() { return "LZMA2:"+(options != null ? options : (31-Integer.numberOfLeadingZeros(dictSize))); }

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

        int size = (2 | (b & 0x1)) << (b / 2 + 11);
        this.dictSize = size;
        if (options != null) options.setDictSize(size);
    }

    public LZMA2Options options() { return options; }
}