package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Arrays;

import static java.lang.Integer.rotateRight;

/**
 * @since 2023/5/29 17:23
 */
public final class Blake3 extends BufferedDigest implements MessageAuthenticCode {
    private static final int BLOCK_LEN = 64, CHUNK_LEN = 1024;

    private static final byte[] SIGMA = new byte[]{2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8};
    private static final int[] IV = new int[]{1779033703, -1150833019, 1013904242, -1521486534, 1359893119, -1694144372, 528734635, 1541459225};

    private final int[] Key, Vec, Msg;
    private final byte[] Pos;
    private int keyLen;
    private int flags;

    private int rootFlags, rootLength;

    private long chunkCount;
    private int chunkLen;

    private int[] stack;
    private int stackSize, stackLength;

    public Blake3(int keyLen) {
        super("Blake3", 64);

        this.Key = new int[8];
        this.Vec = new int[16];
        this.Msg = new int[16];
        this.Pos = new byte[16];

        this.keyLen = keyLen;

        setKeyDefault();

        stackLength = 16;
        stack = new int[stackLength*8];
    }

    @Override
    protected int engineGetDigestLength() { return keyLen; }

    @Override
    protected void engineReset() {
        chunkLen = 0;
        chunkCount = 0;
        rootFlags = 0;
        stackSize = 0;
    }

    @Override
    protected void engineUpdateBlock(DynByteBuf b) {
        setChunk(Vec, BLOCK_LEN, false);
        for (int i = 0; i < 16; ++i) Msg[i] = b.readIntLE();
        compress();
        if (chunkLen == 0) adjustStack();
    }
    private void adjustStack() {
        int i = Long.numberOfTrailingZeros(chunkCount);
        for (;i>0;i--) {
            System.arraycopy(stack, 8*--stackSize, Msg, 0, 8);
            System.arraycopy(Vec, 0, Msg, 8, 8);
            setChunkParent(Vec);
            compress();
        }

        if (stackSize == stackLength) stack = Arrays.copyOf(stack, (stackLength <<= 1)*8);
        System.arraycopy(Vec, 0, stack, 8*stackSize++, 8);
    }

    @Override
    protected void engineDigest(ByteList in, DynByteBuf out) {
        if (rootFlags == 0) {
            in.compact(); // useless, generally

            setChunk(Vec, in.readableBytes(), true);

            int i = in.wIndex();
            while (i < 64) in.list[i++] = 0;
            in.wIndex(64);

            for (i = 0; i < 16; ++i) Msg[i] = in.readIntLE(i<<2);
            in.clear();

            compress();
            while (stackSize > 0) {
                System.arraycopy(stack, 8 * --stackSize, Msg, 0, 8);
                System.arraycopy(Vec, 0, Msg, 8, 8);
                setChunkParent(Vec);
                if (stackSize == 0) setRoot();
                compress();
            }
        }

        getMoreDigest(out, keyLen);
    }

    public void getMoreDigest(DynByteBuf out, int len) {
        if (buf.isReadable()) {
            int l = Math.min(buf.readableBytes(), len);
            out.put(buf, l);
            len -= l;
        }

        while (len >= BLOCK_LEN) {
            buf.clear();
            setChunkOutput();
            out.put(buf);
            len -= BLOCK_LEN;
        }

        if (len > 0) {
            buf.clear();
            setChunkOutput();
            out.put(buf, len);
            buf.rIndex = len;
        } else {
            buf.clear();
        }
    }

    private void compress() {
        for (byte i = 0; i < Pos.length; i++) Pos[i] = i;
        for (int i = 0; i < 6; i++) {
            round();
            // permute
            for (byte j = 0; j < Pos.length; ++j) Pos[j] = SIGMA[Pos[j]];
        }

        round();
        int[] v = Vec;

        if (rootFlags == 0) {
            for (int i = 0; i < 8; ++i) v[i] ^= v[i+8];
            return;
        }

        buf.rIndex = 0; buf.wIndex(64);
        for (int i = 0; i < 8; i++) {
            buf.putIntLE(i*4, v[i]^v[i+8])
               .putIntLE(i*4+32, v[i+8]^stack[i]);
        }
    }

    private void round() {
        // CHAINING0, CHAINING1, CHAINING2, CHAINING3
        // CHAINING4, CHAINING5, CHAINING6, CHAINING7
        //       IV0,       IV1,       IV2,       IV3
        //    COUNT0,    COUNT1,  DATA_LEN,     FLAGS
        G(0, 0, 4, 8, 12);
        G(1, 1, 5, 9, 13);
        G(2, 2, 6, 10, 14);
        G(3, 3, 7, 11, 15);
        G(4, 0, 5, 10, 15);
        G(5, 1, 6, 11, 12);
        G(6, 2, 7, 8, 13);
        G(7, 3, 4, 9, 14);
    }
    private void G(int id, int a, int b, int c, int d) {
        id <<= 1;
        int[] v = Vec;
        v[a] += v[b] + Msg[Pos[id++]];
        v[d] = rotateRight(v[d] ^ v[a], 16);
        v[c] += v[d];
        v[b] = rotateRight(v[b] ^ v[c], 12);
        v[a] += v[b] + Msg[Pos[id]];
        v[d] = rotateRight(v[d] ^ v[a], 8);
        v[c] += v[d];
        v[b] = rotateRight(v[b] ^ v[c], 7);
    }

    @Override
    public void init(byte[] b, int off, int len) {
        if (b == null || len == 0) {
            setKeyDefault();
            flags = 0;
        } else {
            if (len != 32) throw new IllegalStateException("length must be 0 or 32");
            ByteList bl = IOUtil.SharedBuf.get().wrap(b, off, len);
            for (int i = 0; i < 8; ++i) Key[i] = bl.readIntLE();
            flags = KEYED_HASH;
        }

        reset();
    }
    public Blake3 initKDF(DynByteBuf key) {
        engineReset();

        setKeyDefault();
        flags = DERIVE_KEY_CONTEXT;

        update(key);
        int len = keyLen;
        keyLen = 0;
        digest(new ByteList());
        keyLen = len;

        System.arraycopy(Vec, 0, Key, 0, 8);
        flags = DERIVE_KEY_MATERIAL;

        engineReset();
        return this;
    }

    private static final int CHUNK_START = 1, CHUNK_END = 2;
    private static final int PARENT = 4, ROOT = 8;
    private static final int KEYED_HASH = 16, DERIVE_KEY_CONTEXT = 32, DERIVE_KEY_MATERIAL = 64;

    private void setKeyDefault() { System.arraycopy(IV, 0, Key, 0, 8); }

    private void setChunk(int[] Vec, int len, boolean last) {
        if (chunkLen == 0) System.arraycopy(Key, 0, Vec, 0, 8);
        System.arraycopy(IV, 0, Vec, 8, 4);

        Vec[12] = (int) chunkCount;
        Vec[13] = (int) (chunkCount >> 32);
        Vec[14] = len;
        Vec[15] = flags | (chunkLen == 0 ? CHUNK_START : 0) | (last ? CHUNK_END : 0);

        chunkLen += len;
        if (chunkLen >= CHUNK_LEN) {
            chunkCount++;
            chunkLen = 0;
            Vec[15] |= CHUNK_END;
        }

        if (last && stackSize == 0) setRoot();
    }
    private void setChunkParent(int[] Vec) {
        System.arraycopy(Key, 0, Vec, 0, 8);
        System.arraycopy(IV, 0, Vec, 8, 4);
        Vec[12] = 0;
        Vec[13] = 0;
        Vec[14] = 64;
        Vec[15] = flags|PARENT;
    }
    private void setChunkOutput() {
        chunkCount++;
        System.arraycopy(stack, 0, Vec, 0, 8);
        System.arraycopy(IV, 0, Vec, 8, 4);
        Vec[12] = (int) chunkCount;
        Vec[13] = (int) (chunkCount >> 32);
        Vec[14] = rootLength;
        Vec[15] = rootFlags;
        compress();
    }

    private void setRoot() {
        rootLength = Vec[14];
        rootFlags = Vec[15] |= ROOT;
        chunkCount = 0;
        System.arraycopy(Vec, 0, stack, 0, 8);
    }
}