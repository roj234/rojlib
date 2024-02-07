package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.concurrent.OperationDone;
import roj.crypt.HMAC;
import roj.crypt.KeyAgreement;
import roj.crypt.RCipherSpi;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import static roj.net.mss.MSSEngineClient.HELLO_RETRY_REQUEST;

/**
 * MSS服务端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public class MSSEngineServer extends MSSEngine {
	public MSSEngineServer() {}
	public MSSEngineServer(SecureRandom rnd) {super(rnd);}

	public static final int VERIFY_CLIENT = 0x2;

	@Override
	public final void switches(int sw) {
		final int AVAILABLE_SWITCHES = PSC_ONLY | VERIFY_CLIENT;
		int i = sw & ~AVAILABLE_SWITCHES;
		if (i != 0) throw new IllegalArgumentException("Illegal switch:"+i);

		flag = (byte) sw;
	}

	// region inheritor modifiable

	protected IntMap<MSSKeyPair> preSharedCerts;
	protected MSSSessionManager sessionManager;

	public void setPreSharedCertificate(IntMap<MSSPublicKey> certs) {
		assertInitial();
		if (certs != null && !certs.isEmpty()) {
			for (MSSPublicKey key : certs.values()) {
				if (!(key instanceof MSSKeyPair))
					throw new IllegalArgumentException("Should be private key pair");
			}
		}
		preSharedCerts = Helpers.cast(certs);
	}

	public void setSessionManager(MSSSessionManager sm) {
		assertInitial();
		sessionManager = sm;
	}

	public MSSSessionManager getSessionManager() { return sessionManager; }

	protected CharMap<CipherSuite> getCipherSuiteMap() {
		CharMap<CipherSuite> map = new CharMap<>(cipherSuites.length);
		for (CipherSuite c : cipherSuites) {
			map.put((char) c.id, c);
		}
		return map;
	}

	protected void onPreData(DynByteBuf o) throws MSSException {}

	protected boolean clientShouldReply() { return (flag & VERIFY_CLIENT) != 0; }

	// endregion
	// region Solid Handshake Progress

	private RCipherSpi preDecoder;

	@Override
	public final boolean isClientMode() { return false; }

	@Override
	@SuppressWarnings("fallthrough")
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int v = ensureWritable(tx, toWrite.readableBytes()+4);
			if (v != 0) return v;

			tx.put(H_SERVER_HELLO).putMedium(toWrite.readableBytes()).put(toWrite);
			free(toWrite);
			toWrite = null;

			flag &= ~WRITE_PENDING;
			if (!clientShouldReply()) stage = HS_DONE;
			return HS_OK;
		}

		// fast-fail preventing useless waiting
		if (stage == CLIENT_HELLO && rx.isReadable() &&
			(rx.get(rx.rIndex) != H_CLIENT_HELLO && rx.get(rx.rIndex) != P_ALERT))
			return error(ILLEGAL_PACKET, "not mss protocol");

		int lim = rx.wIndex();
		int type = readPacket(rx);
		if (type < 0) return type;
		int lim2 = rx.wIndex();

		try {
			switch (stage) {
				case CLIENT_HELLO:
				case RETRY_KEY_EXCHANGE:
					if (type != H_CLIENT_HELLO) return error(ILLEGAL_PACKET, "");
					return handleClientHello(tx, rx);
				case PREFLIGHT_END_WAIT:
					if (type == P_PREDATA) return handlePreflight(tx, rx);
					stage = FINISH_WAIT;
				case FINISH_WAIT:
					if (type != H_ENCRYPTED_EXTENSION) return error(ILLEGAL_PACKET, "");
					return handleFinish(tx, rx);
			}
		} catch (GeneralSecurityException e) {
			return error(e);
		} finally {
			rx.rIndex = lim2;
			rx.wIndex(lim);
		}
		return HS_OK;
	}

	public final int handshakeSSL(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int v = ensureWritable(out, toWrite.readableBytes()+4);
			if (v != 0) return v;

			out.put(H_SERVER_HELLO).putMedium(toWrite.readableBytes()).put(toWrite);
			free(toWrite);
			toWrite = null;

			flag &= ~WRITE_PENDING;
			return HS_OK;
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
		if (in.readShort() != 0x0303) return error(VERSION_MISMATCH, "");

		byte[] rnd = sharedKey = new byte[64];
		random.nextBytes(rnd);
		in.read(rnd,0,32);

		int len = in.readUnsignedByte();
		if (len > 32) return error(ILLEGAL_PARAM, "session_id");
		DynByteBuf legacy_session_id = in.slice(len);

		CipherSuite suite = null;
		CharMap<CipherSuite> map = getCipherSuiteMap();
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
		if (sessionManager != null) {

		}

		processExtensions(extIn, extOut, 1);
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

		int v = ensureWritable(out, ob.length() + 4);
		if (v != 0) {
			flag |= WRITE_PENDING;
			toWrite = heapBuffer(ob.length()).put(ob);
			return v;
		}

		out.put(H_SERVER_HELLO).putMedium(ob.length()).put(ob);
		return HS_OK;
	}

	// ID client_hello
	// u4 magic
	// u1 version (反正我一直都是破坏性更改)
	// opaque[32] random
	// u2[2..2^16-2] ciphers
	// u1 key_exchange_type
	// opaque[0..2^16-1] key_exchange_data;
	// u4 support_key_bits
	// extension[]
	private int handleClientHello(DynByteBuf tx, DynByteBuf rx) throws MSSException, GeneralSecurityException {
		if (rx.readInt() != H_MAGIC) return error(ILLEGAL_PACKET, "header");
		if (rx.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "");
		int packetBegin = rx.rIndex;

		byte[] sharedRandom = sharedKey = new byte[64];
		random.nextBytes(sharedRandom);
		rx.read(sharedRandom,0,32);

		CipherSuite suite = null;
		CharMap<CipherSuite> map = getCipherSuiteMap();
		int len = rx.readUnsignedShort();
		if (len > 1024) return error(ILLEGAL_PACKET, "cipher_suite.length");
		int suite_id = -1;
		for (int i = 0; i < len; i++) {
			CipherSuite mySuite = map.get((char) rx.readUnsignedShort());
			if (mySuite != null) {
				suite = mySuite;
				suite_id = i;
				rx.skipBytes((len-i-1) << 1);
				break;
			}
		}
		if (suite == null) return error(NEGOTIATION_FAILED, "cipher_suite");

		KeyAgreement ke = getKeyExchange(rx.readUnsignedByte());
		if (ke == null) {
			if (stage == RETRY_KEY_EXCHANGE) return error(ILLEGAL_PARAM, "hello_retry");
			stage = RETRY_KEY_EXCHANGE;
			rx.skipBytes(rx.readUnsignedShort());

			tx.put(H_SERVER_HELLO).putMedium(43)
			   .put(PROTOCOL_VERSION).put(EMPTY_32).putShort(0xFFFF)
			   .putShort(4).putInt(getSupportedKeyExchanges()).putShort(0);
			return HS_OK;
		} else {
			ke.init(random);

			initKeyDeriver(suite, ke.readPublic(rx.slice(rx.readUnsignedShort())));
		}

		int supported_certificate = rx.readInt();

		CharMap<DynByteBuf> extOut = new CharMap<>();
		CharMap<DynByteBuf> extIn = Extension.read(rx);

		// 0-RTT
		zeroRtt:
		if (sessionManager != null && extIn.containsKey(Extension.session)) {
			DynByteBuf id = extIn.remove(Extension.session);
			MSSSession sess = sessionManager.getOrCreateSession(extIn, id);

			// 如果session不存在or新建则丢弃0-RTT消息
			if (sess == null) break zeroRtt;

			if (sess.key != null) {
				preDecoder = getSessionCipher(sess, Cipher.DECRYPT_MODE);
			}

			sess.key = deriveKey("session", 64);
			sess.suite = suite;

			extOut.put(Extension.session, new ByteList(sess.id));
		}

		// Pre-shared certificate to decrease bandwidth consumption
		MSSKeyPair kp = null;
		if (preSharedCerts != null) {
			DynByteBuf psc = extIn.remove(Extension.pre_shared_certificate);
			if (psc == null) {
				if ((flag & PSC_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate only");
			} else {
				int i;
				while (psc.isReadable()) {
					kp = preSharedCerts.get(i = psc.readInt());
					if (kp != null) {
						extOut.put(Extension.pre_shared_certificate, heapBuffer(4).putInt(i));
						break;
					}
				}
			}
		}

		if (kp == null) {
			kp = getCertificate(extIn, supported_certificate);
			if (kp == null) return error(NEGOTIATION_FAILED, "certificateType");

			byte[] data = kp.encode();
			extOut.put(Extension.certificate, heapBuffer(1+data.length).put(kp.format()).put(data));
		}

		if ((flag & VERIFY_CLIENT) != 0)
			extOut.put(Extension.certificate_request, heapBuffer(4).putInt(getSupportCertificateType()));

		processExtensions(extIn, extOut, 1);

		// ID server_hello
		// u1 version
		// opaque[32] random
		// u2 ciphers (0xFFFF means hello_retry
		// opaque[0..2^16-1] key_share_length
		// extension[] encrypted_ext (encrypt if not rx retry mode)
		// opaque[1..2^8-1] encrypted_signature

		ByteList ob = IOUtil.getSharedByteBuf();
		ob.put(PROTOCOL_VERSION).put(sharedRandom,32,32).putShort(suite_id);
		ke.writePublic(ob.putShort(ke.length()));

		// signer data:
		// key=certificate
		// empty byte 32
		// client_hello body
		// server_hello body (not encrypted)
		HMAC sign = new HMAC(suite.sign.get());
		sign.setSignKey(deriveKey("verify_s", sign.getDigestLength()));
		sign.update(EMPTY_32);
		sign.update(rx.slice(packetBegin, rx.rIndex-packetBegin));

		int pos = ob.wIndex();
		Extension.write(extOut, ob);

		// signature of not encrypted data
		sign.update(ob);
		byte[] bb = CipherSuite.getKeyFormat(kp.format()).sign(kp, random, sign.digestShared());
		ob.putShort(bb.length).put(bb);

		stage = PREFLIGHT_END_WAIT;
		encoder = suite.cipher.get();
		decoder = suite.cipher.get();

		encoder.init(Cipher.ENCRYPT_MODE, deriveKey("s2c0", suite.cipher.getKeySize()), null, getPRNG("s2c"));
		decoder.init(Cipher.DECRYPT_MODE, deriveKey("c2s0", suite.cipher.getKeySize()), null, getPRNG("c2s"));

		ob.rIndex = pos;
		DynByteBuf encb = heapBuffer(encoder.engineGetOutputSize(ob.readableBytes()));
		try {
			encoder.cryptFinal(ob, encb);
			ob.wIndex(pos); ob.put(encb);
			ob.rIndex = 0;
		} finally {
			free(encb);
		}

		int v = ensureWritable(tx, ob.length() + 4);
		if (v != 0) {
			flag |= WRITE_PENDING;
			toWrite = heapBuffer(ob.length()).put(ob);
			return v;
		}

		tx.put(H_SERVER_HELLO).putMedium(ob.length()).put(ob);
		if (!clientShouldReply()) stage = HS_DONE;
		return HS_OK;
	}

	// ID preflight_data
	// opaque[*] encoded_data
	private int handlePreflight(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (preDecoder != null) {
			DynByteBuf o = heapBuffer(preDecoder.engineGetOutputSize(rx.readableBytes()));
			try {
				preDecoder.cryptFinal(rx, o);
				onPreData(o);
			} catch (Exception e) {
				return error(e);
			} finally {
				free(o);
			}
		}
		return HS_OK;
	}

	// ID encrypted_extension
	// extension[] extensions
	private int handleFinish(DynByteBuf tx, DynByteBuf rx) throws MSSException, GeneralSecurityException {
		decoder.cryptInline(rx, rx.readableBytes());

		CharMap<DynByteBuf> extIn = Extension.read(rx);
		if ((flag & VERIFY_CLIENT) != 0) {
			DynByteBuf buf = extIn.remove(Extension.certificate);
			if (buf == null) return error(ILLEGAL_PARAM, "certificate missing");

			byte[] sign = buf.readBytes(buf.readUnsignedShort());

			int type = buf.readUnsignedByte();
			if ((getSupportCertificateType() & (1 << type)) == 0) return error(NEGOTIATION_FAILED, "unsupported_certificate_type");

			MSSPublicKey key = checkCertificate(type, buf);
			try {
				if (!CipherSuite.getKeyFormat(key.format()).verify(key, sharedKey, sign)) throw OperationDone.INSTANCE;
			} catch (Exception e) {
				return error(ILLEGAL_PARAM, "invalid signature");
			}
		}
		processExtensions(extIn, null, 3);

		stage = HS_DONE;
		return HS_OK;
	}

	// endregion
}