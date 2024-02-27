package roj.archive.qz;

import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMAInputStream;
import roj.archive.qz.xz.LZMAOutputStream;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class LZMA extends QZCoder {
    public LZMA() { options = new LZMA2Options(); }
    public LZMA(int level) { options = new LZMA2Options(level); }
    public LZMA(LZMA2Options options) { this.options = options; }
    LZMA(boolean unused) {}

    QZCoder factory() {return new LZMA(true);}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || LZMA.class != o.getClass()) return false;
        LZMA lzma = (LZMA) o;

        if (options != null || lzma.options != null) return getOptions().equals(lzma.getOptions());
		return props == lzma.props && dictSize == lzma.dictSize;
	}
    @Override
    public int hashCode() {
        byte props;
        int dictSize;
        if (options != null) {
            props = options.getPropByte();
            dictSize = options.getDictSize();
        } else {
            props = this.props;
            dictSize = this.dictSize;
        }
        return 31 * props + dictSize;
    }

    private static final byte[] ID = {3, 1, 1};
    byte[] id() {return ID;}

    private byte props;
    private int dictSize;
    private LZMA2Options options;

    public LZMA2Options getOptions() {
        if (options != null) return options;
        return new LZMA2Options().setPropByte(props).setDictSize(dictSize);
    }
    public void setOptions(LZMA2Options options) {this.options = options;}

    @Override
    public OutputStream encode(OutputStream out) throws IOException {return new LZMAOutputStream(out, options, false);}

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
        byte props;
        int dictSize;

        if (options != null) {
            props = options.getPropByte();
            dictSize = options.getDictSize();
        } else {
            props = this.props;
            dictSize = this.dictSize;
        }
        useMemory(memoryLimit, LZMAInputStream.getMemoryUsage(dictSize, props));

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
		props = buf.readByte();
        dictSize = buf.readIntLE();
    }
}