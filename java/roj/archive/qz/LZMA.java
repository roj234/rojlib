package roj.archive.qz;

import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMAInputStream;
import roj.archive.qz.xz.LZMAOutputStream;
import roj.archive.qz.xz.MemoryLimitException;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class LZMA extends QZCoder {
    public LZMA() { options = new LZMA2Options(); }
    public LZMA(int level) { options = new LZMA2Options(level); }
    public LZMA(LZMA2Options options) { this.options = options; }
    LZMA(boolean unused) {}

    QZCoder factory() { return new LZMA(true); }
    private static final byte[] ID = {3,1,1};
    byte[] id() { return ID; }

    private byte props;
    private int dictSize;
    private LZMA2Options options;

    public LZMA2Options getOptions() {
        if (options != null) return options;
        return new LZMA2Options().setPropByte(props).setDictSize(dictSize);
    }
    public void setOptions(LZMA2Options options) { this.options = options; }

    @Override
    public OutputStream encode(OutputStream out) throws IOException { return new LZMAOutputStream(out, options, false); }

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) throws IOException {
        byte props;
        int dictSize;

        if (options != null) {
            props = options.getPropByte();
            dictSize = options.getDictSize();
        } else {
            props = this.props;
            dictSize = this.dictSize;
        }

        int memoryUsage = LZMAInputStream.getMemoryUsage(dictSize, props);
        if (memoryUsage > maxMemoryLimitInKb)
            throw new MemoryLimitException(memoryUsage, maxMemoryLimitInKb);

        LZMAInputStream in1 = new LZMAInputStream(in, uncompressedSize, props, dictSize);
        in1.enableRelaxedEndCondition();
        return in1;
    }

    @Override
    public String toString() { return "LZMA:"+(options != null ? options : (31-Integer.numberOfLeadingZeros(dictSize))); }

    void writeOptions(DynByteBuf buf) {
        if (options != null) {
            buf.put(options.getPropByte()).putIntLE(options.getDictSize());
        } else {
            buf.put(props).putIntLE(dictSize);
        }
    }
    void readOptions(DynByteBuf buf, int length) {
        props = buf.get();
        dictSize = buf.readIntLE();
    }
}
