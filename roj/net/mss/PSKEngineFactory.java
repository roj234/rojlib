package roj.net.mss;

import roj.crypt.PSKFile;

import java.io.File;
import java.security.PublicKey;

/**
 * @author Roj233
 * @since 2022/2/17 20:08
 */
public final class PSKEngineFactory implements MSSEngineFactory {
    private final MSSPubKey[] psk;
    private final boolean pskOnly;

    public PSKEngineFactory(boolean pskOnly, MSSPubKey... psk) {
        this.pskOnly = pskOnly;
        this.psk = psk;
    }

    @Override
    public MSSEngineClient newEngine() {
        MSSEngineClient client = new MSSEngineClient();
        client.setPSK(psk);
        client.setPSKOnly(pskOnly);
        return client;
    }

    public static PSKEngineFactory load(File file, String format) {
        return load(file, format, 0);
    }

    public static PSKEngineFactory load(File file, String format, int identity) {
        if (file.isFile()) {
            PublicKey pk = PSKFile.getInstance(format).load(file);
            if (pk != null) {
                return new PSKEngineFactory(true, new JPubKey(identity, pk));
            }
        }
        return new PSKEngineFactory(false);
    }
}
