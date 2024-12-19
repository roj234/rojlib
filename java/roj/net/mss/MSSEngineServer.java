package roj.net.mss;

import roj.collect.CharMap;
import roj.concurrent.OperationDone;
import roj.crypt.HMAC;
import roj.crypt.RCipherSpi;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;

import static roj.net.mss.MSSContext.PRESHARED_ONLY;
import static roj.net.mss.MSSContext.VERIFY_CLIENT;
import static roj.net.mss.MSSEngineClient.HELLO_RETRY_REQUEST;

/**
 * MSS服务端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
final class MSSEngineServer extends MSSEngine {
	private RCipherSpi preDecoder;
	MSSEngineServer(MSSContext config) {super(config, PRESHARED_ONLY|VERIFY_CLIENT);}
	/**
	 * 开启0-RTT以及Encrypted_Extension
	 */
	private boolean noReply() {return (flag & VERIFY_CLIENT) == 0;}

	@Override public final boolean isClient() {return false;}

	@Override
	@SuppressWarnings("fallthrough")
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int v = ensureWritable(tx, toWrite.readableBytes()+3);
			if (v != 0) return v;

			tx.put(H_SERVER_PACKET).putShort(toWrite.readableBytes()).put(toWrite);
			BufferPool.reserve(toWrite);
			toWrite = null;

			flag &= ~WRITE_PENDING;
			if (preDecoder == null && noReply()) stage = HS_DONE;
			return HS_OK;
		}

		// fast-fail preventing meaningless waiting
		validated:
		if (rx.isReadable()) {
			var type = rx.get(rx.rIndex);
			switch (stage) {
				case CLIENT_HELLO, RETRY_KEY_EXCHANGE:
					if (type == H_CLIENT_PACKET) break validated;
				break;
				case PREFLIGHT_WAIT:
					stage = FINISH_WAIT;
					if (type == P_PREDATA) break validated;
				case FINISH_WAIT:
					preDecoder = null;
					if (type == H_CLIENT_PACKET) break validated;
					if (noReply()) {
						stage = HS_DONE;
						return HS_OK;
					}
			}
			return error(ILLEGAL_PACKET, null);
		}

		int lim = rx.wIndex();
		int type = readPacket(rx);
		if (type < 0) return type;
		int lim2 = rx.wIndex();

		try {
			switch (stage) {
				case CLIENT_HELLO, RETRY_KEY_EXCHANGE: return handleClientHello(tx, rx);
				case PREFLIGHT_WAIT: return handlePreflight(tx, rx);
				case FINISH_WAIT: return handleFinish(tx, rx);
			}
		} catch (GeneralSecurityException e) {
			return error(e);
		} finally {
			rx.rIndex = lim2;
			rx.wIndex(lim);
		}
		return HS_OK;
	}

	// ID client_hello
	// u1 version (反正我一直都是破坏性更改)
	// opaque[32] random
	// u2[2..2^16-2] ciphers
	// u1 key_exchange_type
	// opaque[0..2^16-1] key_exchange_data;
	// u4 support_key_bits
	// extension[]
	private int handleClientHello(DynByteBuf tx, DynByteBuf rx) throws MSSException, GeneralSecurityException {
		if (rx.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, null);
		int packetBegin = rx.rIndex;

		byte[] sharedRandom = sharedKey = new byte[64];
		random.nextBytes(sharedRandom);
		rx.readFully(sharedRandom,0,32);

		CipherSuite suite = null;
		var map = config.getCipherSuites();
		int len = rx.readUnsignedByte();
		for (int i = 0; i < len; i++) {
			if ((suite = map.get(rx.readChar())) != null) {
				rx.skipBytes((len-i-1) << 1);
				break;
			}
		}
		if (suite == null) return error(NEGOTIATION_FAILED, "cipher_suite");

		var ke = config.getKeyExchange(rx.readUnsignedByte());
		if (ke == null) {
			if (stage == RETRY_KEY_EXCHANGE) return error(ILLEGAL_PARAM, "hello_retry");
			stage = RETRY_KEY_EXCHANGE;

			tx.put(H_SERVER_PACKET).putShort(39)
			  .put(PROTOCOL_VERSION).put(EMPTY_32).putShort(0xFFFF).putInt(config.getSupportedKeyExchanges());
			return HS_OK;
		} else {
			ke.init(random);

			initKeyDeriver(suite, ke.readPublic(rx.slice(rx.readUnsignedShort())));
		}

		int supported_certificate = rx.readInt();

		CharMap<DynByteBuf> extOut = new CharMap<>();
		CharMap<DynByteBuf> extIn = Extension.read(rx);

		DynByteBuf tmp;
		// 0-RTT
		zeroRtt:
		if ((tmp = extIn.remove(Extension.session)) != null) {
			var sess = config.getOrCreateSession(context, tmp, 1);

			// 如果session不存在or新建则丢弃0-RTT消息
			if (sess == null) break zeroRtt;

			if (sess.key != null) {
				preDecoder = getSessionCipher(sess, Cipher.DECRYPT_MODE);
			}

			sess.key = deriveKey("session", 64);
			sess.suite = suite;

			extOut.put(Extension.session, new ByteList(sess.id));
		}

		// Pre-shared certificate
		MSSKeyPair kp = null;
		if ((tmp = extIn.remove(Extension.pre_shared_certificate)) != null) {
			var certs = config.getPreSharedCerts();
			if (certs != null) while (tmp.isReadable()) {
				int i;
				kp = certs.get(i = tmp.readInt());
				if (kp != null) {
					extOut.put(Extension.pre_shared_certificate, config.buffer(4).putInt(i));
					break;
				}
			}
		} else {
			if ((flag & PRESHARED_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate only");
		}

		if (kp == null) {
			kp = config.getCertificate(context, extIn, supported_certificate);
			if (kp == null) return error(NEGOTIATION_FAILED, "certificateType");

			byte[] data = kp.encode();
			extOut.put(Extension.certificate, config.buffer(1+data.length).put(kp.format()).put(data));
		}

		if ((flag & VERIFY_CLIENT) != 0)
			extOut.put(Extension.certificate_request, config.buffer(4).putInt(config.getSupportCertificateType()));

		config.processExtensions(context, extIn, extOut, 1);

		// ID server_hello
		// u1 version
		// opaque[32] random
		// u2 ciphers (0xFFFF means hello_retry
		// opaque[0..2^16-1] key_share_length
		// extension[] encrypted_ext (encrypt if not rx retry mode)
		// opaque[1..2^8-1] encrypted_signature

		ByteList ob = IOUtil.getSharedByteBuf();
		ob.put(PROTOCOL_VERSION).put(sharedRandom,32,32).putShort(suite.id);
		ke.writePublic(ob.putShort(ke.length()));

		// signer data:
		// key=certificate
		// client_hello body
		// empty byte 32
		// server_hello body (not encrypted)
		HMAC signData = new HMAC(suite.sign.get());
		signData.setSignKey(deriveKey("verify_s", signData.getDigestLength()));
		signData.update(rx.slice(packetBegin, rx.rIndex-packetBegin));
		signData.update(EMPTY_32);

		int pos = ob.wIndex();
		Extension.write(extOut, ob);

		// signature of not encrypted data
		signData.update(ob);

		byte[] signBytes = CipherSuite.getKeyFormat(kp.format()).sign(kp, random, signData.digestShared());
		ob.putShort(signBytes.length).put(signBytes);

		stage = PREFLIGHT_WAIT;
		encoder = suite.cipher.get();
		decoder = suite.cipher.get();

		var prng = getPRNG("comm_prng");
		// 和Client是反的，因为PRNG顺序
		decoder.init(Cipher.DECRYPT_MODE, deriveKey("c2s0", suite.cipher.getKeySize()), null, prng);
		encoder.init(Cipher.ENCRYPT_MODE, deriveKey("s2c0", suite.cipher.getKeySize()), null, prng);

		ob.rIndex = pos;
		DynByteBuf encb = config.buffer(encoder.engineGetOutputSize(ob.readableBytes()));
		try {
			encoder.cryptFinal(ob, encb);
			ob.wIndex(pos); ob.put(encb);
			ob.rIndex = 0;
		} finally {
			BufferPool.reserve(encb);
		}

		int v = ensureWritable(tx, ob.readableBytes()+3);
		if (v != 0) {
			flag |= WRITE_PENDING;
			toWrite = config.buffer(ob.readableBytes()).put(ob);
			return v;
		}

		tx.put(H_SERVER_PACKET).putShort(ob.readableBytes()).put(ob);
		// 半个通道已经建立：服务器可以发送数据了
		return HS_OK;
	}

	// ID preflight_data
	// opaque[*] encoded_data
	private int handlePreflight(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (preDecoder != null) {
			int outputSize = preDecoder.engineGetOutputSize(rx.readableBytes()) - tx.writableBytes();
			if (outputSize > 0) return outputSize;

			try {
				preDecoder.cryptFinal(rx, tx);
			} catch (Exception e) {
				return error(e);
			}
			return HS_PRE_DATA;
		}
		return HS_BUFFER_UNDERFLOW;
	}

	// ID encrypted_extension
	// extension[] extensions
	private int handleFinish(DynByteBuf tx, DynByteBuf rx) throws MSSException, GeneralSecurityException {
		decoder.cryptInline(rx, rx.readableBytes());

		CharMap<DynByteBuf> extIn = Extension.read(rx);
		if ((flag & VERIFY_CLIENT) != 0) {
			var buf = extIn.remove(Extension.certificate);
			if (buf == null) return error(ILLEGAL_PARAM, "certificate missing");

			byte[] sign = buf.readBytes(buf.readUnsignedShort());

			int type = buf.readUnsignedByte();
			if ((config.getSupportCertificateType() & (1 << type)) == 0) return error(NEGOTIATION_FAILED, "unsupported_certificate_type");

			MSSPublicKey key = config.checkCertificate(context, type, buf, false);
			try {
				if (!CipherSuite.getKeyFormat(key.format()).verify(key, sharedKey, sign)) throw OperationDone.INSTANCE;
			} catch (Exception e) {
				return error(ILLEGAL_PARAM, "invalid signature");
			}
		}
		config.processExtensions(context, extIn, null, 3);

		stage = HS_DONE;
		return HS_OK;
	}

	private int ensureWritable(DynByteBuf out, int len) {
		int size = len - out.writableBytes();
		return size > 0 ? size : 0;
	}

	// region TLS1.3
	private static final byte[] FAKE_SESSION_ID = TextUtil.hex2bytes("e0e1e2e3 e4e5e6e7 e8e9eaeb ecedeeef f0f1f2f3 f4f5f6f7 f8f9fafb fcfdfeff");
	public final int handshakeTLS13(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			// ...
		}

		switch (stage) {
			case INITIAL:
				handleClientHelloSSL(out, in);
				break;
			case SERVER_HELLO:
		}
		return HS_OK;
	}
	private int handleClientHelloSSL(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (in.readableBytes() < 5) return -1;
		int ridx = in.rIndex;
		if (in.readMedium() != 0x160301) return error(ILLEGAL_PACKET, "protocol version");
		if (in.readableBytes() < in.readUnsignedShort()) {
			in.rIndex = ridx;
			return -1;
		}

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
		for (int i = 0; i < len; i++) {
			CipherSuite mySuite = map.get((char) in.readUnsignedShort());
			if (mySuite != null) {
				suite = mySuite;
				in.rIndex += len-i-1;
				break;
			}
		}

		if (suite == null) {
			System.arraycopy(HELLO_RETRY_REQUEST, 0, rnd, 32, 32);
		}

		if (in.readUnsignedShort() != 0x0100) return error(NEGOTIATION_FAILED, "compression_method");

		CharMap<DynByteBuf> extOut = new CharMap<>();
		CharMap<DynByteBuf> extIn = Extension.read(in);

		// 0-RTT
		//if (sessionManager != null) {

		//}

		config.processExtensions(context, extIn, extOut, 1);
		// ProtocolVersion legacy_version = 0x0303;    /* TLS v1.2 */
		// Random random;
		// opaque legacy_session_id_echo<0..32>;
		// CipherSuite cipher_suite;
		// uint8 legacy_compression_method = 0;
		// Extension extensions<6..2^16-1>;

		ByteList ob = IOUtil.getSharedByteBuf();
		ob.putShort(0x0303)
		  .put(rnd,32,32)
		  .put(legacy_session_id.readableBytes()).put(legacy_session_id)
		  .putShort(suite.id)
		  .put(0);

		Extension.write(extOut, ob);

		out.put(H_SERVER_PACKET).putMedium(ob.length()).put(ob);
		return HS_OK;
	}
	// endregion
}