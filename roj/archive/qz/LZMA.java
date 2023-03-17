package roj.archive.qz;

import roj.archive.qz.xz.*;
import roj.util.DynByteBuf;
import roj.util.Helpers;

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

        int pb = props / (9 * 5);
        props -= pb * 9 * 5;
        int lp = props / 9;
        int lc = props - lp * 9;

        LZMA2Options options = new LZMA2Options();
        try {
            options.setPb(pb);
            options.setLcLp(lc, lp);
            options.setDictSize(dictSize);
        } catch (UnsupportedOptionsException e) {
            Helpers.athrow(e);
        }
        return options;
    }
    public void setOptions(LZMA2Options options) {
        this.options = options;
    }

    @Override
    public OutputStream encode(OutputStream out) throws IOException {
        return new LZMAOutputStream(out, options, false);
    }

    @Override
    public InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) throws IOException {
        byte props;
        int dictSize;

        if (options != null) {
            props = (byte) ((options.getPb() * 5 + options.getLp()) * 9 + options.getLc());
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
    public String toString() {
        return "LZMA:"+(31-Integer.numberOfLeadingZeros(options==null?dictSize:options.getDictSize()));
    }

    void writeOptions(DynByteBuf buf) {
        if (options != null) {
            byte propsByte = (byte) ((options.getPb() * 5 + options.getLp()) * 9 + options.getLc());
            buf.put(propsByte).putIntLE(options.getDictSize());
        } else {
            buf.put(props).putIntLE(dictSize);
        }
    }
    void readOptions(DynByteBuf buf, int length) throws IOException {
        props = buf.get();
        dictSize = buf.readIntLE();
        if (dictSize > LZMAInputStream.DICT_SIZE_MAX)
            throw new IOException("词典超过java的支持上限(2GB)");
    }
}
