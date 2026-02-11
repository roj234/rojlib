package roj.archive.sevenz;

import roj.crypt.*;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/3/15 15:32
 */
@SevenZCodecExtension("06f10701")
public final class SevenZAES extends SevenZCodec {
    private static final byte[] id = {6,-15,7,1};
    static {register(id, SevenZAES::new);}

    private final byte cyclePower;
    private final byte[] salt;
    private final byte[] iv = new byte[16];
    private final RCipher cipher = RCipher.getInstance("AES/ECB/NoPadding");

    private static final int CYCLE_POWER_MAX = 24;

    /** 7zip GUI的默认参数 */
    public SevenZAES(String pass) {this(pass, 19, 0);}
    public SevenZAES(String pass, int cyclePower, int saltLength) {this(pass.getBytes(StandardCharsets.UTF_16LE), cyclePower, saltLength);}
    public SevenZAES(byte[] pass, int cyclePower, int saltLength) {
        if ((cyclePower < 0 || cyclePower > CYCLE_POWER_MAX) && cyclePower != 63) throw new IllegalStateException("cyclePower的范围是[0,"+CYCLE_POWER_MAX+"]|63");
        if (saltLength < 0 || saltLength > 16) throw new IllegalStateException("saltLength的范围是[0,16]");

        this.cyclePower = (byte) cyclePower;

        SecureRandom srnd = new SecureRandom();
        srnd.nextBytes(iv);

        if (saltLength == 0) salt = ArrayCache.BYTES;
        else salt = srnd.generateSeed(saltLength);

        setKey(pass);
    }

    private SevenZAES(DynByteBuf props) {
        int b0 = props.readUnsignedByte();
        int b1 = props.readUnsignedByte();

        cyclePower = (byte) (b0 & 0x3f);
        int ivLen = ((b0 >> 6) & 1) + (b1 & 0x0f);
        int saltLen = ((b0 >> 7) & 1) + (b1 >> 4);

        salt = saltLen == 0 ? ArrayCache.BYTES : props.readBytes(saltLen);
        props.readFully(iv, 0, ivLen);

        // new的时候已经零填充了
        //while (ivLen < 16) iv[ivLen++] = 0;
    }

    public byte[] id() {return id;}

    @Override
    public OutputStream encode(OutputStream out) throws IOException {
        if (lastKey == null) throw new IllegalArgumentException("缺少密码");

        var cip = new FeedbackCipher(cipher.copyWith(true), FeedbackCipher.MODE_CBC);
        try {
            cip.init(true, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherOutputStream(out, cip);
    }
    @Override
    public void writeOptions(DynByteBuf props) {
        int info = cyclePower << 8;

        int ivLen = 16;
        while (ivLen > 0 && iv[ivLen-1] == 0) ivLen--;
        info |= ivLen == 16 ? 0x400F : ivLen;

        int saltLen = salt.length;
        info |= saltLen == 16 ? 0x80F0 : saltLen << 4;

        props.putShort(info).put(salt).put(iv, 0, ivLen);
    }

    @Override
    public InputStream decode(InputStream in, byte[] key, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
        if (key == null) throw new IllegalArgumentException("缺少密码");
        setKey(key);

        var cip = new FeedbackCipher(cipher, FeedbackCipher.MODE_CBC);
        try {
            cip.init(false, null, new IvParameterSpecNC(iv), null);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
        return new CipherInputStream(in, cip);
    }

    private byte[] lastKey;
    private void setKey(byte[] key) {
        synchronized (this) {
            if (Arrays.equals(lastKey, key)) return;
            lastKey = key;
        }

        byte[] realKey;

        if (cyclePower == 0x3f) {
            realKey = new byte[32];
            System.arraycopy(salt, 0, realKey, 0, salt.length);
            System.arraycopy(key, 0, realKey, salt.length, Math.min(key.length, 32-salt.length));
        } else {
            if (cyclePower > CYCLE_POWER_MAX) throw new IllegalArgumentException("Cycle Power too large in "+this);
            MessageDigest sha = CryptoFactory.getSharedDigest("SHA-256");
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
            cipher.init(false, realKey);
        } catch (Exception e) {
            Helpers.athrow(e);
        }
    }

    @Override
    public String toString() { return "7zAES:"+cyclePower; }
}