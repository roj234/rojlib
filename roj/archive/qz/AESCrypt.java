package roj.archive.qz;

import roj.crypt.*;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2023/3/15 15:32
 */
public final class AESCrypt extends QZCoder {
    public AESCrypt(String pass) {
        // 7z uses 19 by default (似乎也不能改)
        this(pass, 16, 0);
    }
    public AESCrypt(String pass, int cyclePower, int saltLength) {
        this(pass.getBytes(StandardCharsets.UTF_16LE), cyclePower, saltLength);
    }
    public AESCrypt(byte[] pass, int cyclePower, int saltLength) {
        if (cyclePower < 1 || cyclePower > 63) throw new IllegalStateException("别闹");
        if (saltLength < 0 || saltLength > 16) throw new IllegalStateException("salt length [0,16]");

        this.cyclePower = (byte) cyclePower;

        SecureRandom srnd = new SecureRandom();
        srnd.nextBytes(iv);

        if (saltLength == 0) salt = ArrayCache.BYTES;
        else salt = srnd.generateSeed(saltLength);

        init(pass);
    }
    AESCrypt() {}

    QZCoder factory() { return new AESCrypt(); }
    private static final byte[] ID = {6,-15,7,1};
    byte[] id() { return ID; }

    public OutputStream encode(OutputStream out) throws IOException {
        if (lastKey == null) throw new IOException("缺少密码");

        FeedbackCipher cip = new FeedbackCipher(new AES(dec, false), FeedbackCipher.MODE_CBC);
        try {
            cip.init(Cipher.ENCRYPT_MODE, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherOutputStream(out, cip);
    }
    public InputStream decode(InputStream in, byte[] key, long uncompressedSize, int maxMemoryLimitInKb) throws IOException {
        if (key == null) throw new IOException("缺少密码");
        init(key);

        FeedbackCipher cip = new FeedbackCipher(dec, FeedbackCipher.MODE_CBC);
        try {
            cip.init(Cipher.DECRYPT_MODE, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherInputStream(in, cip);
    }

    private byte[] lastKey;
    private final AES dec = new AES();
    private void init(byte[] key) {
        if (lastKey == key) return;
        lastKey = key;

        byte[] realKey;

        if (cyclePower == 0x3f) {
            realKey = new byte[32];
            System.arraycopy(salt, 2, realKey, 0, salt.length);
            System.arraycopy(key, 0, realKey, salt.length, Math.min(key.length, 32-salt.length));
        } else {
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
                sha.update(counter, 0, 8);
                for (int j = 0; j < 8; j++)
                    if (++counter[j] != 0) break;
            }
            realKey = sha.digest();
        }

        try {
            dec.init(Cipher.DECRYPT_MODE, realKey);
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
        buf.read(iv, 0, ivLen);
        while (ivLen < 16) iv[ivLen++] = 0;
    }

    @Override
    void writeOptions(DynByteBuf buf) {
        int info = cyclePower << 8;
        info |= 0x400F; // iv=16

        int s = salt.length;
        if (s == 16) {
            info |= 0x8000;
            s--;
        }
        info |= s << 4;

        buf.putShort(info).put(salt).put(iv);
    }
}
