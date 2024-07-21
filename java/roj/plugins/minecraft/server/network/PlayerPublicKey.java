package roj.plugins.minecraft.server.network;

import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2024/3/19 0019 20:56
 */
public record PlayerPublicKey(long expiresAt, PublicKey key, byte[] keySignature) {
	private static final KeyFactory rsa;
	static {
		try {
			rsa = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() { return "PPK:hash="+TextUtil.bytes2hex(keySignature).substring(0, 16); }

	public PlayerPublicKey(DynByteBuf buf) { this(buf.readLong(), decodePublicKey(buf.readBytes(buf.readVarInt(512))), buf.readBytes(buf.readVarInt(4096))); }
	public static PublicKey decodePublicKey(byte[] b) {
		try {
			return rsa.generatePublic(new X509EncodedKeySpec(b));
		} catch (Exception e) {
			throw new IllegalArgumentException("invalid rsa publicKey", e);
		}
	}

	public void write(DynByteBuf buf) {
		byte[] encoded = this.key.getEncoded();
		buf.putLong(expiresAt)
		   .putVarInt(encoded.length).put(encoded)
		   .putVarInt(keySignature.length).put(keySignature);
	}

	public void ensureValid(Signature mojangSign, UUID uuid) throws MinecraftException {
		if (hasExpired(0)) throw new MinecraftException("公钥过期");

		try {
			mojangSign.update(signedPayload(uuid));
			if (!mojangSign.verify(keySignature))
				throw new MinecraftException("公钥未经MOJANG签名");
		} catch (MinecraftException e) {
			throw e;
		} catch (Exception e) {
			throw new MinecraftException("签名校验失败", e);
		}
	}

	private byte[] signedPayload(UUID uuid) {
		byte[] encodedKey = this.key.getEncoded();
		byte[] out = new byte[24 + encodedKey.length];
		new ByteList(out).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).putLong(expiresAt).put(encodedKey);
		return out;
	}

	public boolean hasExpired() { return hasExpired(0); }
	public boolean hasExpired(long duration) { return System.currentTimeMillis()+duration > expiresAt; }

	public long expiresAt() { return this.expiresAt; }
	public PublicKey key() { return this.key; }
	public byte[] keySignature() { return this.keySignature; }
}