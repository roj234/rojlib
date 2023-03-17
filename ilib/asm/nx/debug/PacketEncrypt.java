package ilib.asm.nx.debug;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.crypt.AES;
import roj.crypt.FeedbackCipher;
import roj.crypt.RCipherSpi;
import roj.util.ByteList;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.server.SPacketEncryptionRequest;
import net.minecraft.util.CryptManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * @author Roj233
 * @since 2021/10/15 20:31
 */
@Nixim("/")
class PacketEncrypt extends SPacketEncryptionRequest {
	@Shadow
	private String hashedServerId;
	@Shadow
	private PublicKey publicKey;
	@Shadow
	private byte[] verifyToken;

	@Inject("/")
	public void readPacketData(PacketBuffer _lvt_1_) throws IOException {
		this.hashedServerId = _lvt_1_.readString(20);
		byte[] key = _lvt_1_.readByteArray();
		this.verifyToken = _lvt_1_.readByteArray();
		FeedbackCipher mc = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CFB);
		try {
			mc.init(RCipherSpi.DECRYPT_MODE, verifyToken, null, null);
			mc.cryptFinal(ByteList.wrap(key), ByteList.wrapWrite(key));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}
		this.publicKey = CryptManager.decodePublicKey(key);
	}

	@Inject("/")
	public void writePacketData(PacketBuffer buf) throws IOException {
		buf.writeString(this.hashedServerId);

		byte[] encoded = this.publicKey.getEncoded();
		FeedbackCipher mc = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CFB);
		byte[] token = this.verifyToken;
		try {
			mc.init(RCipherSpi.ENCRYPT_MODE, token, null, null);
			mc.cryptFinal(ByteList.wrap(encoded), ByteList.wrapWrite(encoded));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}
		buf.writeByteArray(encoded).writeByteArray(token);
	}
}
