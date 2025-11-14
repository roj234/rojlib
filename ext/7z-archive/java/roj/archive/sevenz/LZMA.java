package roj.archive.sevenz;

import roj.archive.xz.LZMA2Options;
import roj.archive.xz.LZMAInputStream;
import roj.archive.xz.LZMAOutputStream;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class LZMA extends SevenZCodec {
    static final byte[] ID = {3, 1, 1};

    public LZMA() { options = new LZMA2Options(); }
    public LZMA(int level) { options = new LZMA2Options(level); }
    public LZMA(LZMA2Options options) { this.options = options; }
    LZMA(DynByteBuf properties) {
        propByte = properties.readByte();
        dictSize = properties.readIntLE();
    }

    public byte[] id() {return ID;}

    private byte propByte;
    private int dictSize;
    private LZMA2Options options;

    public LZMA2Options getOptions() {
        if (options != null) return options;
        return new LZMA2Options().setPropByte(propByte).setDictSize(dictSize);
    }
    public void setOptions(LZMA2Options options) {this.options = options;}

    @Override
    public OutputStream encode(OutputStream out) throws IOException {return new LZMAOutputStream(out, options, false);}
    @Override
    public void writeOptions(DynByteBuf props) {
        if (options != null) {
            props.put(options.getPropByte()).putIntLE(options.getDictSize());
        } else {
            props.put(propByte).putIntLE(dictSize);
        }
    }

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
        byte propByte;
        int dictSize;

        if (options != null) {
            propByte = options.getPropByte();
            dictSize = options.getDictSize();
        } else {
            propByte = this.propByte;
            dictSize = this.dictSize;
        }
        checkMemoryUsage(memoryLimit, LZMAInputStream.getMemoryUsage(dictSize, propByte));

        LZMAInputStream in1 = new LZMAInputStream(in, uncompressedSize, propByte, dictSize);
        in1.enableRelaxedEndCondition();
        return in1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || LZMA.class != o.getClass()) return false;
        LZMA lzma = (LZMA) o;

        if (options != null || lzma.options != null) return getOptions().equals(lzma.getOptions());
        return propByte == lzma.propByte && dictSize == lzma.dictSize;
    }
    @Override
    public int hashCode() {
        byte propByte;
        int dictSize;
        if (options != null) {
            propByte = options.getPropByte();
            dictSize = options.getDictSize();
        } else {
            propByte = this.propByte;
            dictSize = this.dictSize;
        }
        return 31 * propByte + dictSize;
    }

    @Override
    public String toString() { return "LZMA:"+(options != null ? options : (31-Integer.numberOfLeadingZeros(dictSize))); }
}