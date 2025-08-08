package roj.net.mss;

import org.jetbrains.annotations.Nullable;
import roj.collect.CharMap;
import roj.crypt.HMAC;
import roj.crypt.KeyExchange;
import roj.crypt.RCipher;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.OperationDone;

import java.security.GeneralSecurityException;

/**
 * MSS客户端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
final class MSSEngineClient extends MSSEngine {
	MSSEngineClient(MSSContext config) {super(config, MSSContext.PRESHARED_ONLY);}

	private static final byte HAS_HELLO_RETRY = 0x8;
	private KeyExchange keyExch;

	@Override public final boolean isClient() {return true;}
	@Override public boolean maySendMessage() {return encoder!=null;}

	@Override
	public final int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException {
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int v = toWrite.readableBytes()+3 - tx.writableBytes();
			if (v > 0) return v;
			tx.put(H_CLIENT_PACKET).putShort(toWrite.readableBytes()).put(toWrite);

			if (stage == FINISH_WAIT) {
				toWrite.release();
				toWrite = null;
				stage = HS_DONE;
			}

			flag &= ~WRITE_PENDING;
			return HS_OK;
		}

		switch (stage) {
			case INITIAL:
				return writeClientHello(tx);
			case SERVER_HELLO:
				if (rx.isReadable() && (rx.get(rx.rIndex) != H_SERVER_PACKET && rx.get(rx.rIndex) != P_ALERT))
					return error(ILLEGAL_PACKET, null);

				int lim = rx.wIndex();
				int type = readPacket(rx);
				if (type < 0) return type;
				int lim2 = rx.wIndex();

				try {
					return handleServerHello(tx, rx);
				} catch (GeneralSecurityException e) {
					return error(e);
				} finally {
					rx.rIndex = lim2;
					rx.wIndex(lim);
				}
			default:
				throw new IllegalStateException("Unknown stage "+stage);
		}
	}

	// ID client_hello
	// u1 version (反正我一直都是破坏性更改)
	// opaque[32] random
	// u2[2..2^16-2] ciphers
	// u1 key_exchange_type
	// opaque[0..2^16-1] key_exchange_data;
	// u4 support_key_bits
	// extension[]
	private int writeClientHello(DynByteBuf tx) throws MSSException {
		sharedKey = new byte[64];
		random.nextBytes(sharedKey);

		var cipherSuites = config.getCipherSuites();
		var ob = IOUtil.getSharedByteBuf()
				  .put(PROTOCOL_VERSION)
				  .put(sharedKey,0,32).put(cipherSuites.size());
		for (var suite : cipherSuites.values()) ob.putShort(suite.id);

		var ke = keyExch==null ? keyExch = config.getKeyExchange(-1) : keyExch;
		ke.init(random);
		ke.writePublic(ob.put(CipherSuite.getKeyExchangeId(ke.getAlgorithm())).putShort(ke.length()));

		CharMap<DynByteBuf> ext = new CharMap<>();

		var session = config.getOrCreateSession(context, null, 0);
		if (session != null) {
			if (session.key != null) {
				encoder = getSessionCipher(session, RCipher.ENCRYPT_MODE);
			}
			ext.put(Extension.session, new ByteList(session.id));
		}
		var psc = config.getPreSharedCerts();
		if (psc != null) {
			var tmp = config.buffer(psc.size() << 2);
			for (var entry : psc.selfEntrySet()) tmp.putInt(entry.getIntKey());
			ext.put(Extension.pre_shared_certificate, tmp);
		}
		config.processExtensions(context, null, ext, 0);
		Extension.write(ext, ob.putInt(config.getSupportCertificateType()));

		toWrite = config.buffer(ob.readableBytes()).put(ob);
		stage = SERVER_HELLO;

		return writePacket(tx, ob);
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

		if (rx.readableBytes() <= 38) return error(ILLEGAL_PACKET, null);
		if (rx.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "");
		rx.readFully(sharedKey,32,32);

		var cs_id = rx.readChar();
		if (cs_id == 0xFFFF) {
			if ((flag & HAS_HELLO_RETRY) != 0) return error(ILLEGAL_PARAM, "hello_retry");
			flag |= HAS_HELLO_RETRY;

			if (rx.readableBytes() != 4) return error(ILLEGAL_PACKET, null);
			int ke_avl = config.getSupportedKeyExchanges() & rx.readInt();
			if (ke_avl == 0) return error(NEGOTIATION_FAILED, "key_exchange");

			keyExch = config.getKeyExchange(Integer.numberOfTrailingZeros(ke_avl));
			stage = INITIAL;
			return handshake(tx, rx);
		}

		var suite = config.cipherSuites.get(cs_id);
		if (suite == null) return error(ILLEGAL_PARAM, "cipher_suite");

		initKeyDeriver(suite, keyExch.readPublic(rx.slice(rx.readUnsignedShort())));
		keyExch = null;

		encoder = suite.cipher.get();
		decoder = suite.cipher.get();

		var prng = getPRNG("comm_prng");
		encoder.init(RCipher.ENCRYPT_MODE, deriveKey("c2s0", suite.cipher.getKeySize()), null, prng);
		decoder.init(RCipher.DECRYPT_MODE, deriveKey("s2c0", suite.cipher.getKeySize()), null, prng);

		decoder.cryptInline(rx, rx.readableBytes());
		CharMap<DynByteBuf> extIn = Extension.read(rx);
		CharMap<DynByteBuf> extOut = new CharMap<>();

		var tmp = extIn.remove(Extension.certificate_request);
		if (tmp != null) {
			int formats = tmp.readInt();

			var key = config.getCertificate(context, extIn, formats); // supported
			if (key == null) return error(NEGOTIATION_FAILED, "client_certificate");

			byte[] sign = CipherSuite.getKeyFormat(key.format()).sign(key, random, sharedKey);
			byte[] publicKey = key.encode();
			extOut.put(Extension.certificate, config.buffer(publicKey.length+sign.length+3).putShort(sign.length).put(sign).put(key.format()).put(publicKey));
		}

		MSSPublicKey key;
		if ((tmp = extIn.remove(Extension.pre_shared_certificate)) != null) {
			int id = tmp.readInt();
			var psc = config.getPreSharedCerts();
			if (psc == null || (key = psc.get(id)) == null) return error(ILLEGAL_PARAM, "pre_shared_certificate");
			if (extIn.containsKey(Extension.certificate)) return error(ILLEGAL_PARAM, "certificate mix pre_shared_certificate");
		} else {
			if ((flag & MSSContext.PRESHARED_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate");
			var cert_data = extIn.remove(Extension.certificate);
			if (cert_data == null) return error(ILLEGAL_PARAM, "certificate_missing");

			int type = cert_data.readUnsignedByte();
			if ((config.getSupportCertificateType() & (1 << type)) == 0) return error(NEGOTIATION_FAILED, "unsupported_certificate_type");

			key = config.checkCertificate(context, type, cert_data, true);
		}
		config.processExtensions(context, extIn, extOut, 2);

		var sign = new HMAC(suite.sign.get());
		// signer data:
		// key=certificate
		// client_hello body
		// empty byte 32
		// server_hello body (not encrypted)
		sign.init(deriveKey("verify_s", sign.getDigestLength()));

		// client_hello
		toWrite.rIndex = 1;
		sign.update(toWrite);
		toWrite.release();
		toWrite = null;

		sign.update(EMPTY_32);
		sign.update(rx.slice(inBegin, rx.rIndex-inBegin));

		byte[] signRemote = rx.readBytes(rx.readUnsignedShort());
		try {
			if (!CipherSuite.getKeyFormat(key.format()).verify(key, sign.digestShared(), signRemote))
				throw OperationDone.INSTANCE;
		} catch (Exception e) {
			return error(ILLEGAL_PARAM, "invalid signature");
		}

		if ((tmp = extIn.remove(Extension.session)) != null) {
			var session = config.getOrCreateSession(context, tmp, 2);
			if (session != null) {
				session.key = deriveKey("session", 64);
				session.suite = suite;
			}
		}

		if (!extOut.isEmpty()) {
			var ob = IOUtil.getSharedByteBuf();
			Extension.write(extOut, ob);

			encoder.cryptInline(ob, ob.readableBytes());

			stage = FINISH_WAIT;
			int v = writePacket(tx, ob);
			if (v != 0) return v;
		}

		stage = HS_DONE;
		return HS_OK;
	}

	private int writePacket(DynByteBuf tx, ByteList ob) {
		int v = ob.readableBytes()+3 - tx.writableBytes();
		if (v > 0) {
			flag |= WRITE_PENDING;
			toWrite = config.buffer(ob.readableBytes()).put(ob);
			return v;
		}

		tx.put(H_CLIENT_PACKET).putShort(ob.readableBytes()).put(ob);
		return 0;
	}
}