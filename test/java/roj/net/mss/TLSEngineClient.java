package roj.net.mss;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.CharMap;
import roj.crypt.KeyExchange;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Arrays;

/**
 * MSS客户端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public final class TLSEngineClient extends TLSEngine {
	public TLSEngineClient(MSSContext config) {super(config, MSSContext.PRESHARED_ONLY);}

	private static final byte HAS_HELLO_RETRY = 0x8;
	private KeyExchange keyExch;

	@Override public final boolean isClient() {return true;}
	@Override public boolean maySendMessage() {return encoder!=null;}

	@Override
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		switch (stage) {
			case INITIAL:
				writeClientHello(tx);
				break;
			case SERVER_HELLO:
				if (rx.isReadable() && (rx.getByte(rx.rIndex) != SSL_HANDSHAKE))
					return error(ILLEGAL_PACKET, null);

				int lim = rx.wIndex();
				int type = readPacket(rx);
				if (type < 0) return type;

				try {
					return handleServerHelloSsl(tx, rx);
				} finally {
					rx.rIndex = rx.wIndex();
					rx.wIndex(lim);
				}
		}
		return HS_OK;

	}

	// region TLS1.3
	byte[] serverCookie;

	private void writeClientHello(DynByteBuf out) throws MSSException {
		// u2 legacy_version 0x0303
		var ob = IOUtil.getSharedByteBuf().putZero(9).putShort(0x0303);

		sharedKey = new byte[64];
		random.nextBytes(sharedKey);

		// clientRandom(32)
		ob.put(sharedKey,0,32)

		// len(1) + fakeSessionId(32)
		.put(0x20).put(FAKE_SESSION_ID)

		// cipher suites, each 2 bytes
		.putShort(config.getCipherSuites().size()<<1);
		for (var suite : config.getCipherSuites().values()) ob.writeShort(suite.id);

		// compression method (null)
		ob.putShort(0x01_00);

		// extension
		CharMap<DynByteBuf> ext = new CharMap<>(4);

		// ec point format compression
		ext.put((char) 0x000b, new ByteList().put(0x03).put(0).put(1).put(2));

		// supported groups
		ext.put(SslExtension.supported_groups, new ByteList().putShort(0x0016).putShort(0x0014)
				.putShort(SupportedGroup.x25519) // x25519
				.putShort(SupportedGroup.secp256r1) // secp256r1
				.putShort(SupportedGroup.x448) // x448
				.putShort(SupportedGroup.secp521r1) // secp521r1
				.putShort(SupportedGroup.secp384r1) // secp384r1

				.putShort(SupportedGroup.ffdhe2048) // ffdhe2048
				.putShort(SupportedGroup.ffdhe3072) // ffdhe3072
				.putShort(SupportedGroup.ffdhe4096) // ffdhe4096
				.putShort(SupportedGroup.ffdhe6144) // ffdhe6144
				.putShort(SupportedGroup.ffdhe8192) // ffdhe8192
		);

		// session ticket
		ext.put((char)0x0023, new ByteList());

		// encrypt then MAC (obsoleted)
		ext.put((char)0x0016, new ByteList());

		// extended master secret (obsoleted)
		ext.put((char)0x0017, new ByteList());

		ext.put(SslExtension.signature_algorithms, new ByteList().putShort(10)
				.putShort(0x0403) // ECDSA-SECP256r1-SHA256
				.putShort(0x0503) // ECDSA-SECP384r1-SHA384
				.putShort(0x0603) // ECDSA-SECP521r1-SHA512
				.putShort(0x0807) // ED25519
				.putShort(0x0808) // ED448
		);

		// supported versions
		ext.put(SslExtension.supported_versions, new ByteList().putShort(2).putShort(0x0304));

		// PSK with (EC)DHE key establishment
		ext.put(SslExtension.psk_key_exchange_modes, new ByteList().put(0x01).put(0x01));

		var ke = keyExch==null ? keyExch = config.getKeyExchange(-1) : keyExch;
		ke.init(random);

		ByteList data = new ByteList();
		ke.writePublic(data);
		int dataLen = data.readableBytes();

		// x25519DH
		ext.put(SslExtension.key_share, new ByteList().putShort(dataLen+4).putShort(0x001d).putShort(dataLen).put(data));

		config.processExtensions(context, null, ext, 100);
		Extension.writeSSL(ext, ob);

		buildHandshakeRecord(SSL_HANDSHAKE_CLIENT_HELLO, ob);

		toWrite = config.buffer(ob.readableBytes()).put(ob);
		stage = SERVER_HELLO;

		out.put(ob);
		System.out.println(out.dump());
	}

	private static DynByteBuf buildHandshakeRecord(@MagicConstant(intValues = {SSL_HANDSHAKE_CLIENT_HELLO, SSL_HANDSHAKE_SERVER_HELLO}) byte type, DynByteBuf buf) {
		buildRecord(SSL_HANDSHAKE, buf);
		var len = buf.readableBytes()-9;
		buf.set(5, type);
		buf.setMedium(6, len);
		return buf;
	}

	private static DynByteBuf buildRecord(@MagicConstant(intValues = {SSL_HANDSHAKE, SSL_DATA}) byte type, DynByteBuf buf) {
		var len = buf.readableBytes()-5;
		buf.set(0, type);
		buf.setShort(1, 0x0301);
		buf.setShort(3, (short) len);
		return buf;
	}

	private int handleServerHelloSsl(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (in.readUnsignedShort() != 0x0303) return error(NEGOTIATION_FAILED, "legacy_version");
		in.readFully(sharedKey, 32, 32);
		if (Arrays.equals(sharedKey, 56, 56 + 8, DOWNGRADE_11, 0, 7) &&
			(sharedKey[63]&0xFF) <= 1) {
			return error(ILLEGAL_PARAM, "random");
		}
		if (Arrays.equals(sharedKey, 32, 32 + 32, HELLO_RETRY_REQUEST, 0, 32)) {
			// hello_retry
		}

		if (in.readUnsignedByte() != 0) {
			return error(ILLEGAL_PARAM, "legacy_session_id");
		}

		int suite_id = in.readUnsignedShort();
		//CipherSuite suite = CipherSuites.AESGCM_SHA256;

		// ProtocolVersion legacy_version = 0x0303;    /* TLS v1.2 */
		// Random random;
		// opaque legacy_session_id_echo<0..32>;
		// CipherSuite cipher_suite;
		// uint8 legacy_compression_method = 0;
		// Extension extensions<6..2^16-1>;
		return 0;
	}
	// endregion
}