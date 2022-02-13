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

    final ChaCha   c;
    final Poly1305 p;
    final ByteList tb;

    ByteList aad;
    int flag;

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
        this.flag = flags;
    }

    public final void setAAD(ByteList aad) {
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
                aad = (ByteList) value;
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

    void init() {
        ChaCha c = this.c;
        int[] key = c.key;

        if (prng != null) {
            key[13] = prng.nextInt();
            key[14] = prng.nextInt();
            key[15] = prng.nextInt();
        }

        key[12] = 0;
        c.reset();
        c.KeyStream();

        byte[] l = tb.list;
        Conv.i2b(c.tmp, 0, 8, l, 0);
        p.setKey(l);
    }

    @Override
    public final int crypt(ByteBuffer in, ByteBuffer out) throws AEADBadTagException {
        if ((flag & CipheR.DECRYPT) == 0) {
            if (out.remaining() < in.remaining() + 16) return BUFFER_OVERFLOW;
            bbEncrypt(in, out);
        } else {
            if (out.remaining() < in.remaining() - 16) return BUFFER_OVERFLOW;
            if (!in.hasRemaining()) return OK;
            bbDecrypt(in, out);
        }
        return OK;
    }

    public final void bbEncrypt(ByteBuffer in, ByteBuffer out) {
        init();

        int pos = out.position(), len;
        c.crypt(in, out);

        ByteList aad = this.aad;
        p.update(aad.list, aad.arrayOffset(), len = aad.wIndex());
        while ((len++ & 15) != 0) p.update((byte) 0);

        int lim = out.limit();
        out.limit(out.position()).position(pos);
        p.update(out);
        out.limit(lim);

        len = out.position() - pos;
        while ((len++ & 15) != 0) p.update((byte) 0);

        ByteList tb = this.tb;
        tb.putLongLE(aad.wIndex()).putLongLE(out.position() - pos);
        p.update(tb.list, 0, 16);
        tb.clear();

        p._digestFinal(p.bList, tb);
        out.put(tb.list, 0, 16);
        tb.clear();
    }

    public final void bbDecrypt(ByteBuffer in, ByteBuffer out) throws AEADBadTagException {
        init();

        int lim = in.limit(), pos = in.position();

        in.limit(lim - 16);
        c.crypt(in, out);
        in.limit(lim);

        int len;
        ByteList aad = this.aad;
        p.update(aad.list, aad.arrayOffset(), len = aad.wIndex());
        while ((len++ & 15) != 0) p.update((byte) 0);

        in.limit(in.position()).position(pos);
        p.update(in);
        in.limit(lim);

        len = lim - pos;
        while ((len++ & 15) != 0) p.update((byte) 0);

        ByteList tb = this.tb;
        tb.putLongLE(aad.wIndex()).putLongLE(lim - 16);
        p.update(tb.list, 0, 16);
        tb.clear();

        p._digestFinal(p.bList, tb);

        boolean ok = true;
        //   Validating the authenticity of a message involves a bitwise
        //   comparison of the calculated tag with the received tag.  In most use
        //   cases, nonces and AAD contents are not "used up" until a valid
        //   message is received.  This allows an attacker to send multiple
        //   identical messages with different tags until one passes the tag
        //   comparison.  This is hard if the attacker has to try all 2^128
        //   possible tags one by one.  However, if the timing of the tag
        //   comparison operation reveals how long a prefix of the calculated and
        //   received tags is identical, the number of messages can be reduced
        //   significantly.
        for (int i = 0; i < 4; i++) {
            if (tb.readInt() != in.getInt()) {
                ok = false;
            }
        }
        tb.clear();
        if (!ok) throw new AEADBadTagException();
    }

    @Override
    public final void crypt(ByteList in, ByteList out) throws AEADBadTagException {
        if ((flag & CipheR.DECRYPT) == 0) {
            blEncrypt(in, out);
        } else {
            blDecrypt(in, out);
        }
    }

    public final void blEncrypt(ByteList in, ByteList out) {
        init();

        int pos = out.wIndex(), len;
        c.crypt(in, out);

        ByteList aad = this.aad;
        p.update(aad.list, aad.arrayOffset(), len = aad.wIndex());
        while ((len++ & 15) != 0) p.update((byte) 0);

        p.update(out.list, pos, len = out.wIndex() - pos);
        while ((len++ & 15) != 0) p.update((byte) 0);

        ByteList tb = this.tb;
        tb.putLongLE(aad.wIndex()).putLongLE(out.wIndex() - pos);
        p.update(tb.list, 0, 16);
        tb.clear();

        p._digestFinal(p.bList, out);
    }

    public final void blDecrypt(ByteList in, ByteList out) throws AEADBadTagException {
        init();

        in.wIndex(in.wIndex() - 16);
        c.crypt(in, out);
        in.wIndex(in.wIndex() + 16);

        int len;
        ByteList aad = this.aad;
        p.update(aad.list, aad.arrayOffset(), len = aad.wIndex());
        while ((len++ & 15) != 0) p.update((byte) 0);

        p.update(in.list, in.arrayOffset(), len = in.wIndex() - 16);
        while ((len++ & 15) != 0) p.update((byte) 0);

        ByteList tb = this.tb;
        tb.putLongLE(aad.wIndex()).putLongLE(in.wIndex() - 16);
        p.update(tb.list, 0, 16);
        tb.clear();

        p._digestFinal(p.bList, tb);

        boolean ok = true;
        for (int i = 0; i < 4; i++) {
            if (tb.readInt() != in.readInt()) {
                ok = false;
            }
        }
        tb.clear();
        if (!ok) throw new AEADBadTagException();
    }
}
