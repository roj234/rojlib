package roj.net.mss;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.CharMap;
import roj.crypt.CryptoFactory;
import roj.crypt.HMAC;
import roj.crypt.RCipher;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;

import static roj.net.mss.MSSContext.PRESHARED_ONLY;
import static roj.net.mss.MSSContext.VERIFY_CLIENT;
import static roj.net.mss.MSSEngineClient.P_ALERT;

/**
 * MSS服务端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public final class TLSEngineServer extends TLSEngine {
	static final byte _1RTT_HS = 0x40;

	private RCipher preDecoder;
	public TLSEngineServer(MSSContext config) {super(config, PRESHARED_ONLY|VERIFY_CLIENT);}

	@Override public final boolean isClient() {return false;}
	@Override public boolean maySendMessage() {return stage>=PREFLIGHT_WAIT;}

	@Override
	@SuppressWarnings("fallthrough")
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			// ...
		}

		switch (stage) {
			case INITIAL:
				if (rx.isReadable() && (rx.getByte(rx.rIndex) != SSL_HANDSHAKE && rx.getByte(rx.rIndex) != P_ALERT))
					return error(ILLEGAL_PACKET, null);

				int lim = rx.wIndex();
				int type = readPacket(rx);
				if (type < 0) return type;

				try {
					return handleClientHelloSSL(tx, rx);
				} finally {
					rx.rIndex = rx.wIndex();
					rx.wIndex(lim);
				}
			case SERVER_HELLO:
		}
		return HS_OK;

	}
	private int ensureWritable(DynByteBuf out, int len) {
		int size = len - out.writableBytes();
		return size > 0 ? size : 0;
	}

	// region TLS1.3

	private int handleClientHelloSSL(DynByteBuf out, DynByteBuf in) throws MSSException {
		System.out.println(in.dump());
		var sha384 = CryptoFactory.getMessageDigest("SHA384");
		sha384.update(in.nioBuffer());

		if (in.readUnsignedByte() != 0x01) return error(ILLEGAL_PACKET, "handshake record");
		if (in.readableBytes() < in.readMedium()) return error(ILLEGAL_PACKET, "");
		if (in.readUnsignedShort() != 0x0303) return error(VERSION_MISMATCH, "TLS12");

		byte[] rnd = sharedKey = new byte[64];
		random.nextBytes(rnd);
		in.readFully(rnd,0,32);

		int len = in.readUnsignedByte();
		if (len > 32) return error(ILLEGAL_PARAM, "session_id");
		DynByteBuf legacy_session_id = in.slice(len);

		CipherSuite suite = null;
		CharMap<CipherSuite> map = config.getCipherSuites();
		len = in.readUnsignedShort();
		if (len > 32767) return error(ILLEGAL_PACKET, "cipher_suite.length");
		for (int i = 0; i < len; i += 2) {
			CipherSuite mySuite = map.get((char) in.readUnsignedShort());
			if (mySuite != null) {
				suite = mySuite;
				in.rIndex += len-i-2;
				break;
			}
		}

		if (suite == null) {
			System.arraycopy(HELLO_RETRY_REQUEST, 0, rnd, 32, 32);
		}

		if (in.readUnsignedShort() != 0x0100) return error(NEGOTIATION_FAILED, "compression_method");

		CharMap<DynByteBuf> extOut = new CharMap<>();
		CharMap<DynByteBuf> extIn = Extension.readSSL(in);

		DynByteBuf data = extIn.remove(SslExtension.supported_versions);
		if (data == null || data.readableBytes() != 4 || data.readUnsignedShort() != 0x02 || data.readUnsignedShort() != 0x0304)
			return error(NEGOTIATION_FAILED, "tls1.3 server");

		data.rIndex -= 4;
		extOut.put(SslExtension.supported_versions, data);

		data = extIn.remove(SslExtension.key_share);
		if (data == null || data.readableBytes() < 6) return error(NEGOTIATION_FAILED, "missing key_share");

		data.rIndex += 2;
		int keyExchangeType = data.readUnsignedShort();
		var clientPublic = data.slice(data.readUnsignedShort());

		var ke = config.getKeyExchange(-1);
		ke.init(random);

		var serverPublic = new ByteList();
		ke.writePublic(serverPublic);
		int dataLen = serverPublic.readableBytes();

		try {
			sharedKey = ke.readPublic(clientPublic);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		// x25519DH
		extOut.put(SslExtension.key_share, new ByteList().putShort(dataLen+4).putShort(0x001d).putShort(dataLen).put(serverPublic));

		config.processExtensions(context, extIn, extOut, 1);

		var ob = IOUtil.getSharedByteBuf().putZero(9);
		ob.putShort(0x0303) // server version
		  .put(rnd,32,32) // server random
		  .put(legacy_session_id.readableBytes()).put(legacy_session_id) // session id echo
		  .putShort(suite.id) // cipher suite
		  .put(0); // compression method

		Extension.write(extOut, ob);

		buildHandshakeRecord(ob);

		sha384.update(ob.nioBuffer().position(5));
		byte[] hello_hash = sha384.digest();

		HMAC hmac = new HMAC(sha384);
		var early_secret = CryptoFactory.HKDF_extract(hmac, ArrayCache.BYTES, new byte[32]);
		var derived_secret = HKDF_Expand_Label(hmac, early_secret, "derived", new byte[48]);
		var handshake_secret = CryptoFactory.HKDF_extract(hmac, derived_secret, sharedKey);
		var client_secret = HKDF_Expand_Label(hmac, handshake_secret, "c hs traffic", hello_hash);
		var server_secret = HKDF_Expand_Label(hmac, handshake_secret, "s hs traffic", hello_hash);
		var client_handshake_key = HKDF_Expand_Label(hmac, client_secret, "key", ArrayCache.BYTES);
		var server_handshake_key = HKDF_Expand_Label(hmac, server_secret, "key", ArrayCache.BYTES);
		var client_handshake_iv = HKDF_Expand_Label(hmac, client_secret, "iv", ArrayCache.BYTES);
		var server_handshake_iv = HKDF_Expand_Label(hmac, server_secret, "iv", ArrayCache.BYTES);

		System.out.println("Server key:");
		System.out.println("decryption="+ByteList.wrap(client_handshake_key).hex());
		System.out.println("encryption="+ByteList.wrap(server_handshake_key).hex());

		ob.putInt(0x14030300).putShort(0x0101);

		out.put(ob);
		System.out.println(ob.dump());
		return HS_OK;
	}
	// endregion


	private static DynByteBuf buildHandshakeRecord(DynByteBuf buf) {
		buildRecord(SSL_HANDSHAKE, buf);
		var len = buf.readableBytes()-9;
		buf.set(5, SSL_HANDSHAKE_SERVER_HELLO);
		buf.setMedium(6, len);
		return buf;
	}

	private static DynByteBuf buildRecord(@MagicConstant(intValues = {SSL_HANDSHAKE, SSL_DATA}) byte type, DynByteBuf buf) {
		var len = buf.readableBytes()-5;
		buf.set(0, type);
		buf.setShort(1, 0x0303);
		buf.setShort(3, (short) len);
		return buf;
	}

}