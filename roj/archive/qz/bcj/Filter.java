package roj.archive.qz.bcj;

import roj.crypt.RCipherSpi;
import roj.util.DynByteBuf;

import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * @author Lasse Collin <lasse.collin@tukaani.org>, Roj234
 * @since 2023/3/16 0016 0:06
 */
public abstract class BCJFilter extends RCipherSpi {
    final boolean isEncoder;
    int pos;

    BCJFilter(boolean encoder) {
        isEncoder = encoder;
    }

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

    protected abstract int filter(byte[] b, int off, int len);
}
