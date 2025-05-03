package roj.archive.qz;

import roj.crypt.*;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/3/15 15:32
 */
public final class QzAES extends QZCoder {
    private static final int CYCLE_POWER_MAX = 24;

    //PBKDF2-HMAC-SHA256: 600,000 iterations
    public QzAES(String pass) {this(pass, 19, 0);}
    public QzAES(String pass, int cyclePower, int saltLength) {this(pass.getBytes(StandardCharsets.UTF_16LE), cyclePower, saltLength);}
    public QzAES(byte[] pass, int cyclePower, int saltLength) {
        if ((cyclePower < 0 || cyclePower > CYCLE_POWER_MAX) && cyclePower != 63) throw new IllegalStateException("cyclePower的范围是[0,"+CYCLE_POWER_MAX+"]|63");
        if (saltLength < 0 || saltLength > 16) throw new IllegalStateException("saltLength的范围是[0,16]");

        this.cyclePower = (byte) cyclePower;

        SecureRandom srnd = new SecureRandom();
        srnd.nextBytes(iv);

        if (saltLength == 0) salt = ArrayCache.BYTES;
        else salt = srnd.generateSeed(saltLength);

        init(pass);
    }
    QzAES() {}

    QZCoder factory() {return new QzAES();}
    private static final byte[] ID = {6,-15,7,1};
    byte[] id() {return ID;}

    public OutputStream encode(OutputStream out) throws IOException {
        if (lastKey == null) throw new IllegalArgumentException("缺少密码");

        var cip = new FeedbackCipher(aes.copyWith(true), FeedbackCipher.MODE_CBC);
        try {
            cip.init(RCipherSpi.ENCRYPT_MODE, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherOutputStream(out, cip);
    }
    public InputStream decode(InputStream in, byte[] key, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
        if (key == null) throw new IllegalArgumentException("缺少密码");
        init(key);

        var cip = new FeedbackCipher(aes, FeedbackCipher.MODE_CBC);
        try {
            cip.init(RCipherSpi.DECRYPT_MODE, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherInputStream(in, cip);
    }

    private byte[] lastKey;
    private final RCipherSpi aes = CryptoFactory.AES();
    private void init(byte[] key) {
        if (Arrays.equals(lastKey, key)) return;
        lastKey = key;

        byte[] realKey;

        if (cyclePower == 0x3f) {
            realKey = new byte[32];
            System.arraycopy(salt, 0, realKey, 0, salt.length);
            System.arraycopy(key, 0, realKey, salt.length, Math.min(key.length, 32-salt.length));
        } else {
            if (cyclePower > CYCLE_POWER_MAX) throw new IllegalArgumentException("Cycle Power too large in "+this);
            MessageDigest sha;
            try {
                sha = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                Helpers.athrow(e);
                return;
            }

            byte[] counter = new byte[8];
            for (long i = 1L << cyclePower; i > 0; i--) {
                sha.update(salt);
                sha.update(key);
                sha.update(counter);
                for (int j = 0; j < 8; j++)
                    if (++counter[j] != 0) break;
            }
            realKey = sha.digest();
        }

        try {
            aes.init(RCipherSpi.DECRYPT_MODE, realKey);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
    }

    @Override
    public String toString() { return "7zAES:"+cyclePower;  }

    byte cyclePower;
    byte[] salt;
    final byte[] iv = new byte[16];

    @Override
    void readOptions(DynByteBuf buf, int length) {
        lastKey = null;

        int b0 = buf.readUnsignedByte();
        int b1 = buf.readUnsignedByte();

        cyclePower = (byte) (b0 & 0x3f);
        int ivLen = ((b0 >> 6) & 1) + (b1 & 0x0f);
        int saltLen = ((b0 >> 7) & 1) + (b1 >> 4);

        salt = saltLen == 0 ? ArrayCache.BYTES : buf.readBytes(saltLen);
        buf.readFully(iv, 0, ivLen);
        while (ivLen < 16) iv[ivLen++] = 0;
    }

    @Override
    void writeOptions(DynByteBuf buf) {
        int info = cyclePower << 8;

        int ivLen = 16;
        while (ivLen > 0 && iv[ivLen-1] == 0) ivLen--;
        info |= ivLen == 16 ? 0x400F : ivLen;

        int saltLen = salt.length;
        info |= saltLen == 16 ? 0x80F0 : saltLen << 4;

        buf.putShort(info).put(salt).put(iv, 0, ivLen);
    }
}