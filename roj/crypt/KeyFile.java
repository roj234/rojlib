package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;

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
		KeyPair pair;
		if (!pri.isFile()) {
			try {
				pair = GEN.generateKeyPair();
				try (DataOutputStream out = new DataOutputStream(new FileOutputStream(pri))) {
					byte[] t = pair.getPublic().getEncoded();
					out.writeShort(t.length);
					out.write(t);

					t = pair.getPrivate().getEncoded();
					out.writeShort(t.length);

					XChaCha_Poly1305 cip = new XChaCha_Poly1305();
					cip.setKey(pass, CipheR.ENCRYPT);
					cip.cryptBegin();
					cip.encrypt(ByteList.wrap(t), ByteList.wrapWrite(t));

					out.write(t);
					out.write(cip.getHash().list, 0, 16);
				}

				if (pub != null) {
					try (FileOutputStream dos = new FileOutputStream(pub)) {
						dos.write(pair.getPublic().getEncoded());
					}
				}
			} catch (IOException e) {
				return null;
			}
		} else {
			try(DataInputStream in = new DataInputStream(new FileInputStream(pri))) {
				byte[] pubBytes = new byte[in.readUnsignedShort()];
				in.readFully(pubBytes);

				byte[] priBytes = new byte[16 + in.readUnsignedShort()];
				in.readFully(priBytes);
				ByteList priOut = ByteList.wrapWrite(priBytes);

				XChaCha_Poly1305 cip = new XChaCha_Poly1305();
				cip.setKey(pass, CipheR.DECRYPT);
				cip.crypt(ByteList.wrap(priBytes), priOut);

				PrivateKey pk = FACTORY.generatePrivate(new PKCS8EncodedKeySpec(priOut.toByteArray()));
				PublicKey pu = FACTORY.generatePublic(new X509EncodedKeySpec(pubBytes));
				return new KeyPair(pu, pk);
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
