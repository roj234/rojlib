package roj.crypt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2022/2/17 19:23
 */
public class PSKSimple implements PSKFile {
    public static final PSKSimple DH = new PSKSimple("DH");
    public static final PSKSimple EC = new PSKSimple("EC");

    private KeyPairGenerator GEN;
    private KeyFactory FACTORY;

    public PSKSimple(String alg) {
        try {
            GEN = KeyPairGenerator.getInstance(alg);
            FACTORY = KeyFactory.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    public KeyPair loadOrGenerate(File pri, File pub, byte[] pass) throws GeneralSecurityException {
        KeyPair pair;
        if (!pri.isFile()) {
            try {
                pair = GEN.generateKeyPair();

                byte[] t = pair.getPublic().getEncoded();
                try (FileOutputStream out = new FileOutputStream(pri)) {
                    out.write(t.length >> 8);
                    out.write(t.length);
                    out.write(t);

                    t = pair.getPrivate().getEncoded();
                    out.write(t.length >> 8);
                    out.write(t.length);

                    XChaCha_Poly1305 mc = new XChaCha_Poly1305();
                    mc.setKey(pass, CipheR.ENCRYPT);
                    mc.bbEncrypt(ByteBuffer.wrap(t), ByteBuffer.wrap(t));
                    out.write(t);
                    out.write(mc.getPoly1305().list, 0, 16);
                }
                if (pub != null)
                    try (FileOutputStream out = new FileOutputStream(pub)) {
                        out.write(t.length >> 8);
                        out.write(t.length);
                        out.write(t);
                    }
            } catch (IOException e) {
                return null;
            }
        } else {
            try {
                try (FileInputStream in = new FileInputStream(pri)) {
                    int len = (in.read() << 8) | in.read();
                    byte[] t = new byte[len];
                    if (in.read(t) != t.length) return null;
                    PublicKey pubk = FACTORY.generatePublic(new X509EncodedKeySpec(t));

                    len = (in.read() << 8) | in.read();
                    t = new byte[len + 16];
                    if (in.read(t) != t.length) return null;

                    XChaCha_Poly1305 mc = new XChaCha_Poly1305();
                    mc.setKey(pass, CipheR.DECRYPT);
                    mc.bbDecrypt(ByteBuffer.wrap(t), ByteBuffer.wrap(t));

                    PrivateKey prik = FACTORY.generatePrivate(
                            new X509EncodedKeySpec(Arrays.copyOf(t, t.length - 16)));

                    pair = new KeyPair(pubk, prik);
                }
            } catch (IOException e) {
                return null;
            }
        }
        return pair;
    }

    @Override
    public PublicKey load(File pub) {
        try (FileInputStream in = new FileInputStream(pub)) {
            int len = (in.read() << 8) | in.read();
            byte[] t = new byte[len];
            if (in.read(t) != t.length) return null;

            return FACTORY.generatePublic(new X509EncodedKeySpec(t));
        } catch (IOException | GeneralSecurityException e) {
            return null;
        }
    }
}
