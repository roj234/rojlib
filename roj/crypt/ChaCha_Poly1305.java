package roj.crypt;

import roj.util.ByteList;

import javax.crypto.AEADBadTagException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * ChaCha_Poly1305: <br>
 * <a href="https://www.rfc-editor.org/info/rfc8439">RFC8439</a>
 * @author solo6975
 * @since 2022/2/14 13:40
 */
public class ChaCha_Poly1305 implements CipheR {
    public static final String NONCE = "IV", AAD = "AAD", PRNG = "PRNG";
    static final int INITED = 2;

    final ChaCha   c;
    final Poly1305 p;
    final ByteList tb;

    private byte[] aad;
    private byte flag;
    private long processed;

    Random prng;

    public ChaCha_Poly1305() {
        this(new ChaCha());
    }

    public ChaCha_Poly1305(ChaCha chacha) {
        this.c = chacha;
        this.p = new Poly1305();
        this.tb = new ByteList(32);
    }

    public final void setPRNG(Random rng) {
        this.prng = rng;
    }

    public final Random getPRNG() {
        return prng;
    }

    @Override
    public String name() {
        return "ChaCha_Poly1305";
    }

    @Override
    public final void setKey(byte[] key, int flags) {
        c.setKey(key, flags);
        this.flag = (byte) flags;
    }

    public final void setAAD(byte[] aad) {
        this.aad = aad;
    }

    // Note that it is not acceptable to use a truncation of a counter encrypted with a
    // 128-bit or 256-bit cipher, because such a truncation may repeat after a short time.
    public final void setNonce(byte[] nonce) {
        c.setNonce(nonce);
    }

    @Override
    public final void setOption(String key, Object value) {
        switch (key) {
            case NONCE:
                c.setNonce((byte[]) value);
                break;
            case AAD:
                aad = (byte[]) value;
                break;
            case PRNG:
                prng = (Random) value;
                break;
        }
    }

    @Override
    public final int getBlockSize() {
        return 0;
    }

    @Override
    public int getCryptSize(int data) {
        return (flag & DECRYPT) != 0 ? data - 16 : data + 16;
    }

    private void init() {
        ChaCha c = this.c;
        int[] key = c.key;

        if (prng != null) {
            generateNonce(key);
        }

        key[12] = 0;
        c.reset();
        c.KeyStream();

        byte[] l = tb.list;
        Conv.i2b(c.tmp, 0, 8, l, 0);

        Poly1305 p = this.p;
        p.setKey(l);

        byte[] aad = this.aad;
        if (aad != null) {
            int len = aad.length;
            p.update(aad);
            while ((len++ & 15) != 0) p.update((byte) 0);
        }

        processed = 0;
        flag |= INITED;
    }

    void generateNonce(int[] key) {
        key[13] = prng.nextInt();
        key[14] = prng.nextInt();
        key[15] = prng.nextInt();
    }

    @Override
    public final int crypt(ByteBuffer in, ByteBuffer out) throws AEADBadTagException {
        if ((flag & CipheR.DECRYPT) == 0) {
            if (out.remaining() < in.remaining() + 16) return BUFFER_OVERFLOW;
            bbEncrypt(in, out);
            out.put(getPoly1305().list, 0, 16);
        } else {
            if (out.remaining() < in.remaining() - 16) return BUFFER_OVERFLOW;
            if (!in.hasRemaining()) return OK;
            bbDecrypt(in, out);
        }
        return OK;
    }

    public final void bbEncrypt(ByteBuffer in, ByteBuffer out) {
        if ((flag & INITED) == 0) init();

        int pos = out.position(), lim = out.limit();
        c.crypt(in, out);
        out.limit(out.position()).position(pos);
        p.update(out);
        out.limit(lim);

        processed += out.position() - pos;
    }

    public final void bbDecrypt(ByteBuffer in, ByteBuffer out) throws AEADBadTagException {
        init();

        int lim = in.limit(), pos = in.position();

        in.limit(lim - 16);
        p.update(in);

        in.position(pos);
        c.crypt(in, out);
        in.limit(lim);
        processed = lim - pos - 16;

        ByteList tb = getPoly1305();
        boolean ok = true;
        //  If the timing of the tag comparison operation reveals how long
        //  a prefix of the calculated and received tags is identical.
        for (int i = 0; i < 4; i++) {
            if (tb.readInt() != in.getInt()) {
                ok = false;
            }
        }
        if (!ok) throw new AEADBadTagException();
    }

    @Override
    public final void crypt(ByteList in, ByteList out) throws AEADBadTagException {
        if ((flag & CipheR.DECRYPT) == 0) {
            blEncrypt(in, out);
            out.put(getPoly1305().list, 0, 16);
        } else {
            blDecrypt(in, out);
        }
    }

    public final void blEncrypt(ByteList in, ByteList out) {
        if ((flag & INITED) == 0) init();

        int pos = out.wIndex();
        c.crypt(in, out);
        p.update(out.list, pos, pos = out.wIndex() - pos);

        processed += pos;
    }

    public final void blDecrypt(ByteList in, ByteList out) throws AEADBadTagException {
        init();

        in.wIndex(in.wIndex() - 16);
        c.crypt(in, out);
        in.wIndex(in.wIndex() + 16);

        int len;
        p.update(in.list, in.arrayOffset(), len = in.wIndex() - 16);
        processed = len;

        ByteList tb = getPoly1305();
        boolean ok = true;
        for (int i = 0; i < 4; i++) {
            if (tb.readInt() != in.readInt()) {
                ok = false;
            }
        }
        if (!ok) throw new AEADBadTagException();
    }

    public final ByteList getPoly1305() {
        int len = (int) (processed & 15);
        while ((len++ & 15) != 0) p.update((byte) 0);

        ByteList tb = this.tb;
        tb.clear();
        tb.putLongLE(aad == null ? 0 : aad.length).putLongLE(processed);
        p.update(tb.list, 0, 16);
        tb.clear();

        p._digestFinal(p.bList, tb);

        flag &= ~INITED;

        return tb;
    }
}
