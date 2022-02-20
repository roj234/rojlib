package roj.crypt;

import roj.net.mss.MSSSubKey;
//import sun.security.util.CurveDB;
//import sun.security.util.NamedCurve;
//
//import javax.crypto.KeyAgreement;
//import java.nio.ByteBuffer;
//import java.security.*;
//import java.security.spec.AlgorithmParameterSpec;
//import java.security.spec.InvalidKeySpecException;
//import java.security.spec.X509EncodedKeySpec;
//import java.util.Collection;
//import java.util.Iterator;
//import java.util.Random;

/**
 * @author solo6975
 * @since 2022/2/12 15:31
 */
public abstract class ECDH implements MSSSubKey {
//    public static final Collection<? extends NamedCurve> CURVES = CurveDB.getSupportedCurves();
//
//    private final KeyPairGenerator ecpg;
//    private final KeyFactory eckf;
//    private final KeyAgreement ecka;
//    private final AlgorithmParameterSpec curve;
//
//    private byte[] data;
//
//    public ECDH() {
//        this(CURVES.iterator().next());
//    }
//
//    public ECDH(AlgorithmParameterSpec curve) {
//        this.curve = curve;
//        try {
//            ecpg = KeyPairGenerator.getInstance("EC");
//            eckf = KeyFactory.getInstance("EC");
//            ecka = KeyAgreement.getInstance("ECDH");
//        } catch (NoSuchAlgorithmException e) {
//            throw new Error(e);
//        }
//    }
//
//    public static ECDH randomly(Random r) {
//        int i = r.nextInt(CURVES.size());
//        Iterator<? extends NamedCurve> itr = CURVES.iterator();
//        while (true) {
//            NamedCurve curve = itr.next();
//            if (i-- == 0) return new ECDH(curve);
//        }
//    }
//
//    @Override
//    public void initA(Random r, int sharedRandom) {
//        try {
//            SecureRandom r1 = r instanceof SecureRandom ? (SecureRandom) r : null;
//
//            ecpg.initialize(curve, r1);
//            KeyPair eckp = ecpg.generateKeyPair();
//            ecka.init(eckp.getPrivate(), r1);
//
//            data = eckp.getPublic().getEncoded();
//        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public int length() {
//        return data.length;
//    }
//
//    @Override
//    public void clear() {
//        data = null;
//    }
//
//    @Override
//    public void writeA(ByteBuffer bb) {
//        bb.put(data);
//    }
//
//    @Override
//    public byte[] readA(ByteBuffer bb) {
//        byte[] data = new byte[bb.remaining()];
//        bb.get(data);
//        try {
//            ecka.doPhase(eckf.generatePublic(new X509EncodedKeySpec(data)), true);
//        } catch (InvalidKeyException | InvalidKeySpecException ignored) {}
//        return ecka.generateSecret();
//    }
}
