package ilib.asm.nx.debug;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.crypt.AES;
import roj.crypt.MyCipher;
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
		MyCipher sm4 = new MyCipher(new AES(), MyCipher.MODE_CFB);
		sm4.setKey(verifyToken, MyCipher.DECRYPT);
		try {
			sm4.crypt(ByteList.wrap(key), ByteList.wrap(key));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}
		this.publicKey = CryptManager.decodePublicKey(key);
	}

	@Inject("/")
	public void writePacketData(PacketBuffer buf) throws IOException {
		buf.writeString(this.hashedServerId);

		byte[] encoded = this.publicKey.getEncoded();
		MyCipher sm4 = new MyCipher(new AES(), MyCipher.MODE_CFB);
		byte[] token = this.verifyToken;
		sm4.setKey(token, MyCipher.ENCRYPT);
		try {
			sm4.crypt(ByteList.wrap(encoded), ByteList.wrap(encoded));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}
		buf.writeByteArray(encoded).writeByteArray(token);
	}
}
