package roj.crypt;

/**
 * @author solo6975
 * @since 2022/2/14 20:31
 */
public final class XChaCha_Poly1305 extends ChaCha_Poly1305 {
    private final byte[] tmpNonce = new byte[24];

    public XChaCha_Poly1305() {
        super(new XChaCha());
    }

    public XChaCha_Poly1305(XChaCha chacha) {
        super(chacha);
    }

    @Override
    public String name() {
        return "XChaCha_Poly1305";
    }

    @Override
    void init() {
        ChaCha c = this.c;
        int[] key = c.key;

        if (prng != null) {
            prng.nextBytes(tmpNonce);
            c.setNonce(tmpNonce);
        }

        key[12] = 0;
        c.reset();
        c.KeyStream();

        byte[] l = tb.list;
        Conv.i2b(c.tmp, 0, 8, l, 0);
        p.setKey(l);
    }
}
