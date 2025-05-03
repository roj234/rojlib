package roj.archive.qz.bcj;

import roj.crypt.RCipherSpi;
import roj.util.DynByteBuf;

import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * @author Roj234
 * @since 2023/3/16 0:06
 */
public abstract class Filter extends RCipherSpi {
    final boolean encode;
    int pos;

    Filter(boolean encode) { this.encode = encode; }

    public static int checkStartOffset(int pos, int alignment) {
        if ((pos & (alignment-1)) != 0) throw new IllegalArgumentException("Start offset must be a multiple of " + alignment);
        return pos;
    }

	public final void init(int flags, byte[] key, AlgorithmParameterSpec config, SecureRandom random) {}

    // hack Cipher I/O
    public final int engineGetBlockSize() { return -1; }

    public final void crypt(DynByteBuf in, DynByteBuf out) {
        assert in.array() == out.array();

        int len = filter(in.array(), in.relativeArrayOffset(), in.readableBytes());
        in.rIndex += len;
        out.wIndex(out.wIndex()+len);
    }
    protected void cryptFinal1(DynByteBuf in, DynByteBuf out) {
        out.wIndex(out.wIndex()+in.readableBytes());
        in.rIndex = in.wIndex();
    }

    protected abstract int filter(byte[] b, int off, int len);
}
