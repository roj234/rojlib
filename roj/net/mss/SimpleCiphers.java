package roj.net.mss;

import roj.crypt.CipheR;
import roj.crypt.MyCipher;

import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/14 17:30
 */
public class SimpleCiphers implements MSSCiphers {
    protected final int keySize, mcFlag;
    protected final Supplier<CipheR> provider;
    protected final boolean stream;

    public SimpleCiphers(int keySize, Supplier<CipheR> provider) {
        this.keySize = keySize;
        this.provider = provider;
        this.mcFlag = 0;
        this.stream = provider.get().getBlockSize() == 0;
    }

    public SimpleCiphers(int keySize, Supplier<CipheR> provider, int mcFlag) {
        this.keySize = keySize;
        this.provider = provider;
        this.mcFlag = mcFlag;
        switch (mcFlag) {
            case MyCipher.MODE_CFB:
            case MyCipher.MODE_CTR:
            case MyCipher.MODE_OFB:
                stream = true;
                break;
            default:
                stream = false;
        }
    }

    @Override
    public boolean isStreamCipher() {
        return stream;
    }

    @Override
    public int getKeySize() {
        return keySize;
    }

    @Override
    public CipheR createEncoder() {
        CipheR r = provider.get();
        if (mcFlag != 0) {
            r = new MyCipher(r, mcFlag);
        }
        return r;
    }

    @Override
    public CipheR createDecoder() {
        return createEncoder();
    }
}
