package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.concurrent.OperationDone;
import roj.crypt.HMAC;
import roj.crypt.KeyAgreement;
import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * MSS客户端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public class MSSEngineClient extends MSSEngine {
	public MSSEngineClient() {}
	public MSSEngineClient(SecureRandom rnd) {super(rnd);}

	private static final byte HAS_HELLO_RETRY = 0x8;

	@Override
	public final void switches(int sw) {
		final int AVAILABLE_SWITCHES = PSC_ONLY;
		int i = sw & ~AVAILABLE_SWITCHES;
		if (i != 0) throw new IllegalArgumentException("Illegal switch:"+i);

		flag = (byte) sw;
	}

	// region inheritor modifiable

	protected MSSSession session;
	protected String serverName, alpn;
	protected IntMap<MSSPublicKey> psc;

	@Override
	public void setPreSharedCertificate(IntMap<MSSPublicKey> certs) {
		assertInitial();
		this.psc = certs;
	}

	public MSSEngineClient session(MSSSession s) {
		assertInitial();
		this.session = s;
		return this;
	}
	public MSSSession getSession() { return session; }
	public MSSEngineClient serverName(String s) {
		assertInitial();
		this.serverName = s;
		return this;
	}
	public MSSEngineClient alpn(String s) {
		assertInitial();
		this.alpn = s;
		return this;
	}

	// endregion
	// region Solid Handshake Process

	private KeyAgreement keyExch;

	@Override
	public final boolean isClientMode() { return true; }

	@Override
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int v = ensureWritable(tx, toWrite.readableBytes()+4);
			if (v != 0) return v;
			tx.put(stage==INITIAL? H_CLIENT_HELLO : H_ENCRYPTED_EXTENSION).putMedium(toWrite.readableBytes()).put(toWrite);

			if (stage != INITIAL) {
				free(toWrite);
				toWrite = null;
				stage = HS_DONE;
			}
			flag &= ~WRITE_PENDING;
			return HS_OK;
		}

		switch (stage) {
			case INITIAL:
				// ID client_hello
				// u4 magic
				// u1 version (反正我一直都是破坏性更改)
				// opaque[32] random
				// u2[2..2^16-2] ciphers
				// u1 key_exchange_type
				// opaque[0..2^16-1] key_exchange_data;
				// u4 support_key_bits
				// extension[]
				ByteList ob = IOUtil.getSharedByteBuf().putInt(H_MAGIC).put(PROTOCOL_VERSION);
				sharedKey = new byte[64];
				random.nextBytes(sharedKey);
				// should <= 1024
				ob.put(sharedKey,0,32).putShort(cipherSuites.length);
				for (CipherSuite suite : cipherSuites) {
					ob.putShort(suite.id);
				}

				KeyAgreement ke = keyExch==null ? keyExch = getKeyExchange(-1) : keyExch;
				ke.init(random);
				ke.writePublic(ob.put(CipherSuite.getKeyAgreementId(ke.getAlgorithm())).putShort(ke.length()));

				CharMap<DynByteBuf> ext = new CharMap<>();
				if (session != null) {
					if (session.key != null) {
						encoder = getSessionCipher(session, Cipher.ENCRYPT_MODE);
					}
					ext.put(Extension.session, new ByteList(session.id));
				}
				if (psc != null) {
					DynByteBuf tmp = heapBuffer(psc.size() << 2);
					for (IntMap.Entry<MSSPublicKey> entry : psc.selfEntrySet()) {
						tmp.putInt(entry.getIntKey());
					}
					ext.put(Extension.pre_shared_certificate, tmp);
				}
				if (serverName != null) {
					ext.put(Extension.server_name, new ByteList().putUTFData(serverName));
				}
				if (alpn != null) {
					ext.put(Extension.application_layer_protocol, new ByteList().putUTFData(alpn));
				}
				processExtensions(null, ext, 0);
				Extension.write(ext, ob.putInt(getSupportCertificateType()));

				toWrite = heapBuffer(ob.readableBytes()).put(ob);
				stage = SERVER_HELLO;

				int v = ensureWritable(tx, ob.length()+4);
				if (v != 0) {
					flag |= WRITE_PENDING;
					return v;
				}
				tx.put(H_CLIENT_HELLO).putMedium(ob.length()).put(ob);
				break;
			case SERVER_HELLO:
				if (rx.isReadable() && (rx.get(rx.rIndex) != H_SERVER_HELLO && rx.get(rx.rIndex) != P_ALERT))
					return error(ILLEGAL_PACKET, "not mss server");

				int lim = rx.wIndex();
				int type = readPacket(rx);
				if (type < 0) return type;

				try {
					return handleServerHello(tx, rx);
				} catch (GeneralSecurityException e) {
					return error(e);
				} finally {
					rx.rIndex = lim;
					rx.wIndex(lim);
				}
		}
		return HS_OK;
	}

	//          client_hello(1),
	//          server_hello(2),
	//          new_session_ticket(4),
	//          end_of_early_data(5),
	//          encrypted_extensions(8),
	//          certificate(11),
	//          certificate_request(13),
	//          certificate_verify(15),
	//          finished(20),
	//          key_update(24),
	//          message_hash(254),
	static final byte SSL_HANDSHAKE_CLIENT_HELLO = 1, SSL_HANDSHAKE_SERVER_HELLO = 2, SSL_HANDSHAKE_FINISHED = 20, SSL_HANDSHAKE = 22, SSL_DATA = 23;
	static final byte[] HELLO_RETRY_REQUEST = IOUtil.SharedCoder.get().decodeHex("CF21AD74E59A6111BE1D8C021E65B891C2A211167ABB8C5E079E09E2C8A8339C");
	static final byte[] DOWNGRADE_12 = IOUtil.SharedCoder.get().decodeHex("444F574E47524401");
	static final byte[] DOWNGRADE_11 = IOUtil.SharedCoder.get().decodeHex("444F574E47524400");
	byte[] serverCookie;
	public final int handshakeSSL(DynByteBuf out, DynByteBuf in) throws MSSException {
		// to be filled

		if (stage == HS_DONE) return HS_OK;

		switch (stage) {
			case INITIAL:
				// u2 legacy_version 0x0303
				ByteList ob = IOUtil.getSharedByteBuf().putShort(0x0303);

				// opaque[32] random
				sharedKey = new byte[64];
				random.nextBytes(sharedKey);
				ob.put(sharedKey,0,32);

				// opaque legacy_session_id<0..32>;
				ob.put(0);

				// u1 cipher_suites<2..2^16-2>;
				ob.putShort(cipherSuites.length<<1);
				for (CipherSuite suite : cipherSuites) ob.writeShort(suite.id);

				// opaque legacy_compression_methods<1..2^8-1>;
				ob.putShort(0x01_00);

				// Extension extensions<8..2^16-1>;
				CharMap<DynByteBuf> ext = new CharMap<>(4);

				// ProtocolVersion versions<2..254>;
				ext.put(SslExtension.supported_versions, ByteList.wrap(new byte[]{2,3,4}));

				ByteList buf1 = new ByteList();


				buf1.putShort(8);
				buf1.putShort(SupportedGroup.secp256r1).putShort(SupportedGroup.secp384r1).putShort(SupportedGroup.secp521r1)
					.putShort(SupportedGroup.ffdhe2048);
				ext.put(SslExtension.supported_groups, ByteList.wrap(buf1.toByteArray()));

				if (session != null) {

				}
				if (psc != null) {

				}
				//  A "supported_groups" (Section 4.2.7) extension which indicates the
				//      (EC)DHE groups which the client supports and a "key_share"
				//      (Section 4.2.8) extension which contains (EC)DHE shares for some
				//      or all of these groups.

				processExtensions(null, ext, 100);
				Extension.writeSSL(ext, ob);

				toWrite = heapBuffer(ob.readableBytes()).put(ob);
				stage = SERVER_HELLO;

				int v = ensureWritable(out, ob.readableBytes()+4);
				if (v != 0) {
					flag |= WRITE_PENDING;
					return v;
				}

				out.put(SSL_HANDSHAKE).putShort(0x0303).putShort(ob.length()+4).put(SSL_HANDSHAKE_CLIENT_HELLO).putMedium(ob.length()).put(ob);
				System.out.println(out.dump());
				break;
			case SERVER_HELLO:
				if (in.isReadable() && (in.get(in.rIndex) != SSL_HANDSHAKE_SERVER_HELLO && in.get(in.rIndex) != P_ALERT))
					return error(ILLEGAL_PACKET, null);

				int lim = in.wIndex();
				int type = readPacket(in);
				if (type < 0) return type;

				try {
					return handleServerHelloSsl(out, in);
				} finally {
					in.rIndex = in.wIndex();
					in.wIndex(lim);
				}
		}
		return HS_OK;
	}

	private int handleServerHelloSsl(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (in.readUnsignedShort() != 0x0303) return error(NEGOTIATION_FAILED, "legacy_version");
		in.read(sharedKey, 32, 32);
		if (ArrayUtil.rangedEquals(sharedKey,56,8,DOWNGRADE_11,0,7) &&
			(sharedKey[63]&0xFF) <= 1) {
			return error(ILLEGAL_PARAM, "random");
		}
		if (ArrayUtil.rangedEquals(sharedKey,32,32,HELLO_RETRY_REQUEST,0,32)) {
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

	static class SupportedGroup {
		/* Elliptic Curve Groups  = ECDHE */
		static final char secp256r1 = 0x0017, secp384r1 = 0x0018, secp521r1 = 0x0019,
		x25519 = 0x001D, x448 = 0x001E,

		/* Finite Field Groups  = DHE */
		ffdhe2048 = 0x0100, ffdhe3072 = 0x0101, ffdhe4096 = 0x0102,
		ffdhe6144 = 0x0103, ffdhe8192 = 0x0104;
	}

	static class SslExtension {
		static final char
		server_name = 0,                             /* RFC 6066 */
		max_fragment_length = 1,                     /* RFC 6066 */
		status_request = 5,                          /* RFC 6066 */
		supported_groups = 10,                       /* RFC 8422, 7919 */
		signature_algorithms = 13,                   /* RFC 8446 */
		use_srtp = 14,                               /* RFC 5764 */
		heartbeat = 15,                              /* RFC 6520 */
		application_layer_protocol_negotiation = 16, /* RFC 7301 */
		signed_certificate_timestamp = 18,           /* RFC 6962 */
		client_certificate_type = 19,                /* RFC 7250 */
		server_certificate_type = 20,                /* RFC 7250 */
		padding = 21,                                /* RFC 7685 */
		pre_shared_key = 41,                         /* RFC 8446 */
		early_data = 42,                             /* RFC 8446 */
		supported_versions = 43,                     /* RFC 8446 */
		cookie = 44,                                 /* RFC 8446 */
		psk_key_exchange_modes = 45,                 /* RFC 8446 */
		certificate_authorities = 47,                /* RFC 8446 */
		oid_filters = 48,                            /* RFC 8446 */
		post_handshake_auth = 49,                    /* RFC 8446 */
		signature_algorithms_cert = 50,              /* RFC 8446 */
		key_share = 51                              /* RFC 8446 */
		;
	}

	// ID server_hello
	// u1 version
	// opaque[32] random
	// u2 ciphers (0xFFFF means hello_retry
	// opaque[0..2^16-1] key_exchange_data
	// extension[] encrypted_ext (encrypted if not in retry mode)
	// opaque[1..2^8-1] encrypted_signature
	private int handleServerHello(DynByteBuf tx, DynByteBuf rx) throws MSSException, GeneralSecurityException {
		int inBegin = rx.rIndex;

		if (rx.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "");
		rx.read(sharedKey,32,32);

		int cs_id = rx.readUnsignedShort();
		if (cs_id == 0xFFFF) {
			if ((flag & HAS_HELLO_RETRY) != 0) return error(ILLEGAL_PARAM, "hello_retry");
			flag |= HAS_HELLO_RETRY;

			if (rx.readUnsignedShort() != 4) return error(ILLEGAL_PACKET, "");
			int ke_avl = getSupportedKeyExchanges() & rx.readInt();
			if (ke_avl == 0) return error(NEGOTIATION_FAILED, "key_exchange");

			keyExch = getKeyExchange(Integer.numberOfTrailingZeros(ke_avl));
			stage = INITIAL;
			return handshake(tx, rx);
		}

		if (cs_id >= cipherSuites.length) return error(ILLEGAL_PARAM, "cipher_suite");
		CipherSuite suite = cipherSuites[cs_id];

		initKeyDeriver(suite, keyExch.readPublic(rx.slice(rx.readUnsignedShort())));
		keyExch = null;

		encoder = suite.cipher.get();
		decoder = suite.cipher.get();

		encoder.init(Cipher.ENCRYPT_MODE, deriveKey("c2s0", suite.cipher.getKeySize()), null, getPRNG("c2s"));
		decoder.init(Cipher.DECRYPT_MODE, deriveKey("s2c0", suite.cipher.getKeySize()), null, getPRNG("s2c"));

		decoder.cryptInline(rx, rx.readableBytes());
		CharMap<DynByteBuf> extIn = Extension.read(rx);
		CharMap<DynByteBuf> extOut = new CharMap<>();

		if (extIn.containsKey(Extension.certificate_request)) {
			// noinspection all
			int formats = extIn.remove(Extension.certificate_request).readInt();

			MSSKeyPair key = getCertificate(extIn, formats); // supported
			if (key == null) return error(NEGOTIATION_FAILED, "client_certificate");

			byte[] sign = CipherSuite.getKeyFormat(key.format()).sign(key, random, sharedKey);
			byte[] publicKey = key.encode();
			extOut.put(Extension.certificate, heapBuffer(publicKey.length+sign.length+3).putShort(sign.length).put(sign).put(key.format()).put(publicKey));
		}

		MSSPublicKey key;
		if (extIn.containsKey(Extension.pre_shared_certificate)) {
			// noinspection all
			int id = extIn.remove(Extension.pre_shared_certificate).readUnsignedShort();
			if (psc == null || !psc.containsKey(id)) return error(ILLEGAL_PARAM, "pre_shared_certificate");
			key = psc.get(id);
			if (extIn.containsKey(Extension.certificate)) {
				return error(ILLEGAL_PARAM, "certificate and pre_shared_certificate");
			}
		} else {
			if ((flag & PSC_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate");
			DynByteBuf cert_data = extIn.remove(Extension.certificate);
			if (cert_data == null) return error(ILLEGAL_PARAM, "certificate_missing");

			int type = cert_data.readUnsignedByte();
			if ((getSupportCertificateType() & (1 << type)) == 0) return error(NEGOTIATION_FAILED, "unsupported_certificate_type");

			key = checkCertificate(type, cert_data);
		}
		processExtensions(extIn, extOut, 2);

		HMAC sign = new HMAC(suite.sign.get());
		// signer data:
		// key=certificate
		// empty byte 32
		// client_hello body
		// server_hello body (not encrypted)
		sign.setSignKey(deriveKey("verify_s", sign.getDigestLength()));
		sign.update(EMPTY_32);

		// client_hello
		toWrite.rIndex = 5;
		sign.update(toWrite);
		free(toWrite);
		toWrite = null;

		sign.update(rx.slice(inBegin, rx.rIndex-inBegin));
		byte[] signRemote = rx.readBytes(rx.readUnsignedShort());
		try {
			if (!CipherSuite.getKeyFormat(key.format()).verify(key, sign.digestShared(), signRemote))
				throw OperationDone.INSTANCE;
		} catch (Exception e) {
			return error(ILLEGAL_PARAM, "invalid signature");
		}

		DynByteBuf sessid = extIn.remove(Extension.session);
		if (session != null && sessid != null) {
			if (!sessid.equals(new ByteList(session.key)))
				session = new MSSSession(sessid.toByteArray());
			session.key = deriveKey("session", 64);
			session.suite = suite;
		}

		if (!extOut.isEmpty()) {
			ByteList ob = IOUtil.getSharedByteBuf();
			Extension.write(extOut, ob);

			encoder.cryptInline(ob, ob.readableBytes());

			int v = ensureWritable(tx, ob.readableBytes()+4);
			if (v != 0) {
				flag |= WRITE_PENDING;
				toWrite = heapBuffer(ob.readableBytes()).put(ob);
				return v;
			}
			tx.put(H_ENCRYPTED_EXTENSION).putMedium(ob.readableBytes()).put(ob);
		}

		stage = HS_DONE;
		return HS_OK;
	}

	// endregion
}