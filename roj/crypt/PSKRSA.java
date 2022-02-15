package roj.crypt;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2022/2/17 19:23
 */
public class PSKRSA implements PSKFile {
    public static final KeyPairGenerator GEN;
    public static final KeyFactory FACTORY;
    static {
        try {
            GEN = KeyPairGenerator.getInstance("RSA");
            FACTORY = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new Error();
        }
    }

    @Override
    public KeyPair loadOrGenerate(File pri, File pub, byte[] pass) throws GeneralSecurityException {
        KeyPair pair;
        if (!pri.isFile()) {
            try {
                pair = GEN.generateKeyPair();
                try (FileOutputStream fos = new FileOutputStream(pri)) {
                    saveRSAKey(pair, fos, pass);
                }
                if (pub != null)
                    try (FileOutputStream dos = new FileOutputStream(pub)) {
                        RSAPublicKey pk = (RSAPublicKey) pair.getPublic();
                        byte[] t = pk.getPublicExponent().toByteArray();
                        dos.write(t.length);
                        dos.write(t);

                        t = pk.getModulus().toByteArray();
                        dos.write(t.length);
                        dos.write(t);
                    }
            } catch (IOException e) {
                return null;
            }
        } else {
            try {
                pair = loadRSAKey(new FileInputStream(pri), pass);
            } catch (IOException e) {
                return null;
            }
        }
        return pair;
    }

    public static void saveRSAKey(KeyPair kp, OutputStream out, byte[] pass) throws IOException {
        RSAPublicKey pk = (RSAPublicKey) kp.getPublic();
        byte[] t = pk.getPublicExponent().toByteArray();
        out.write(t.length >> 8);
        out.write(t.length);
        out.write(t);

        RSAPrivateKey pr = (RSAPrivateKey) kp.getPrivate();
        t = pr.getPrivateExponent().toByteArray();
        out.write(t.length >> 8);
        out.write(t.length);

        XChaCha_Poly1305 mc = new XChaCha_Poly1305();
        mc.setKey(pass, CipheR.ENCRYPT);
        mc.bbEncrypt(ByteBuffer.wrap(t), ByteBuffer.wrap(t));
        out.write(t);
        out.write(mc.getPoly1305().list, 0, 16);

        t = pr.getModulus().toByteArray();
        out.write(t.length >> 8);
        out.write(t.length);
        out.write(t);
    }

    public static KeyPair loadRSAKey(InputStream in, byte[] pass) throws IOException, GeneralSecurityException {
        byte[] t = new byte[(in.read() << 8) | in.read()];
        if (in.read(t) != t.length) return null;
        BigInteger publicExp = new BigInteger(t);

        t = new byte[16 + ((in.read() << 8) | in.read())];
        if (in.read(t) != t.length) return null;

        XChaCha_Poly1305 mc = new XChaCha_Poly1305();
        mc.setKey(pass, CipheR.DECRYPT);
        mc.crypt(ByteBuffer.wrap(t), ByteBuffer.wrap(t));
        BigInteger privExp = new BigInteger(Arrays.copyOf(t, t.length - 16));

        t = new byte[(in.read() << 8) | in.read()];
        if (in.read(t) != t.length) return null;
        BigInteger mod = new BigInteger(t);

        PrivateKey pk = FACTORY.generatePrivate(new RSAPrivateKeySpec(mod, privExp));
        PublicKey pu = FACTORY.generatePublic(new RSAPublicKeySpec(mod, publicExp));
        return new KeyPair(pu, pk);
    }

    @Override
    public PublicKey load(File pub) {
        try (FileInputStream di = new FileInputStream(pub)) {
            byte[] t = new byte[di.read() & 0xFF];
            if (di.read(t) != t.length) return null;
            BigInteger exp = new BigInteger(t);

            t = new byte[di.read() & 0xFF];
            if (di.read(t) != t.length) return null;
            BigInteger mod = new BigInteger(t);

            return FACTORY.generatePublic(new RSAPublicKeySpec(mod, exp));
        } catch (IOException | GeneralSecurityException e) {
            return null;
        }
    }
}
