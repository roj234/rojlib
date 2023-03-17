package roj.crypt;

import roj.io.IOUtil;

import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @since 2022/2/17 19:23
 */
public class KeyFile {
	private final KeyPairGenerator GEN;
	private final KeyFactory FACTORY;

	public static KeyFile getInstance(String alg) {
		try {
			return new KeyFile(alg);
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	public KeyFile(String alg) throws NoSuchAlgorithmException {
		GEN = KeyPairGenerator.getInstance(alg);
		FACTORY = KeyFactory.getInstance(alg);
	}

	public KeyFile setKeySize(int size) {
		GEN.initialize(size);
		return this;
	}

	public KeyPair getKeyPair(File pri, File pub, byte[] pass) throws GeneralSecurityException {
		FeedbackCipher c = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);
		pass = HMAC.Sha256ExpandKey(pass, null, 32);

		KeyPair pair;
		if (!pri.isFile()) {
			try {
				pair = GEN.generateKeyPair();

				try (OutputStream dos = new FileOutputStream(pub)) {
					dos.write(pair.getPublic().getEncoded());
				}

				try (OutputStream out = new FileOutputStream(pri)) {
					byte[] iv = SecureRandom.getSeed(16);
					out.write(iv);

					c.init(RCipherSpi.ENCRYPT_MODE, pass, new IvParameterSpecNC(iv), null);
					CipherOutputStream cos = new CipherOutputStream(out, c);
					cos.write(pair.getPrivate().getEncoded());
					cos.close();
				}
			} catch (IOException e) {
				return null;
			}
		} else {
			try {
				PublicKey pu = getPublic(pub);

				try (InputStream in = new FileInputStream(pri)) {
					byte[] iv = new byte[16];
					IOUtil.readFully(in, iv, 0, 16);
					c.init(RCipherSpi.DECRYPT_MODE, pass, new IvParameterSpecNC(iv), null);

					PrivateKey pk = FACTORY.generatePrivate(new PKCS8EncodedKeySpec(IOUtil.read(new CipherInputStream(in, c))));
					return new KeyPair(pu, pk);
				}
			} catch (IOException e) {
				return null;
			}
		}
		return pair;
	}

	public PublicKey getPublic(File pub) throws IOException, GeneralSecurityException {
		return FACTORY.generatePublic(new X509EncodedKeySpec(IOUtil.read(pub)));
	}
}
