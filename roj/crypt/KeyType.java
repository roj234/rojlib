package roj.crypt;

import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @since 2022/2/17 19:23
 */
public class KeyType {
	private final KeyPairGenerator GEN;
	private final KeyFactory FACTORY;

	public static KeyType getInstance(String alg) {
		try {
			return new KeyType(alg);
		} catch (NoSuchAlgorithmException e) {
			return Helpers.maybeNull();
		}
	}

	public KeyType(String alg) throws NoSuchAlgorithmException {
		GEN = KeyPairGenerator.getInstance(alg);
		FACTORY = KeyFactory.getInstance(alg);
	}

	public KeyType setKeySize(int size) {
		GEN.initialize(size);
		return this;
	}

	public KeyPair getKeyPair(File pri, File pub, byte[] pass) throws GeneralSecurityException {
		FeedbackCipher c = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);
		pass = HMAC.Sha256ExpandKey(pass, null, 32);

		KeyPair pair;
		if (!pri.isFile()) {
			try {
				pair = generateKey();
				saveKey(pair, pass, pri);

				try (OutputStream dos = new FileOutputStream(pub)) {
					dos.write(pair.getPublic().getEncoded());
				}
			} catch (IOException e) {
				return null;
			}
		} else {
			try {
				return loadKey(pass, pri);
			} catch (IOException e) {
				return null;
			}
		}
		return pair;
	}

	public KeyPair generateKey() { return GEN.generateKeyPair(); }
	public void saveKey(KeyPair kp, byte[] pass, File f) throws IOException, GeneralSecurityException {
		pass = HMAC.Sha256ExpandKey(pass, null, 32);
		FeedbackCipher c = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);

		try (OutputStream out = new FileOutputStream(f)) {
			byte[] iv = SecureRandom.getSeed(16);
			out.write(iv);

			c.init(RCipherSpi.ENCRYPT_MODE, pass, new IvParameterSpecNC(iv), null);
			CipherOutputStream cos = new CipherOutputStream(out, c);

			byte[] b;
			if (!kp.getPublic().getFormat().equals("X.509")) {
				b = FACTORY.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class).getEncoded();
			} else {
				b = kp.getPublic().getEncoded();
			}
			cos.write(b.length >>> 8);
			cos.write(b.length);
			cos.write(b);

			if (!kp.getPrivate().getFormat().equals("PKCS#8")) {
				b = FACTORY.getKeySpec(kp.getPublic(), PKCS8EncodedKeySpec.class).getEncoded();
			} else {
				b = kp.getPrivate().getEncoded();
			}
			cos.write(b.length >>> 8);
			cos.write(b.length);
			cos.write(b);

			cos.close();
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}
	public KeyPair loadKey(byte[] pass, File f) throws IOException {
		pass = HMAC.Sha256ExpandKey(pass, null, 32);
		FeedbackCipher c = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);

		try (InputStream in = new FileInputStream(f)) {
			byte[] iv = new byte[16];
			IOUtil.readFully(in, iv);

			c.init(RCipherSpi.DECRYPT_MODE, pass, new IvParameterSpecNC(iv), null);
			CipherInputStream cis = new CipherInputStream(in, c);

			int len = (cis.read() << 8) | cis.read();
			byte[] key = new byte[len];
			IOUtil.readFully(cis, key);
			PublicKey pub = FACTORY.generatePublic(new X509EncodedKeySpec(key));

			len = (cis.read() << 8) | cis.read();
			key = new byte[len];
			IOUtil.readFully(cis, key);
			PrivateKey pri = FACTORY.generatePrivate(new PKCS8EncodedKeySpec(key));

			cis.close();
			return new KeyPair(pub, pri);
		} catch (Exception e) {
			Helpers.athrow(e);
			return Helpers.maybeNull();
		}
	}

	public PublicKey getPublic(File pub) throws IOException, GeneralSecurityException {
		return FACTORY.generatePublic(new X509EncodedKeySpec(IOUtil.read(pub)));
	}
}
