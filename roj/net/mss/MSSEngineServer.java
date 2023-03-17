package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.CipheR;
import roj.crypt.HMAC;
import roj.crypt.KeyAgreement;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.ComboRandom;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * MSS服务端
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public abstract class MSSEngineServer extends MSSEngine {
	public MSSEngineServer() {}
	public MSSEngineServer(SecureRandom rnd) {super(rnd);}

	public static final int VERIFY_CLIENT = 0x8;

	@Override
	public final void switches(int sw) {
		final int AVAILABLE_SWITCHES = PSC_ONLY | ALLOW_0RTT | VERIFY_CLIENT | STREAM_DECRYPT;
		int i = sw & ~AVAILABLE_SWITCHES;
		if (i != 0) throw new IllegalArgumentException("Illegal switch:"+i);

		flag = (byte) i;
	}

	// region inheritor modifiable

	protected IntMap<MSSPrivateKey> preSharedCerts;

	public void setPSC(IntMap<MSSPublicKey> keys) {
		if (stage != INITIAL) throw new IllegalStateException();
		if (keys != null && !keys.isEmpty()) {
			for (MSSPublicKey key : keys.values()) {
				if (!(key instanceof MSSPrivateKey))
					throw new IllegalArgumentException("Should be private key pair");
			}
		}
		preSharedCerts = Helpers.cast(keys);
	}

	protected MSSSession getSessionById(DynByteBuf id) {
		return null;
	}
	protected DynByteBuf generateSessionId(MSSSession session) {
		return null;
	}

	protected abstract MSSPrivateKey getCertificate(CharMap<DynByteBuf> ext, int supportedFormat);

	protected CharMap<CipherSuite> getCipherSuiteMap() {
		CharMap<CipherSuite> map = new CharMap<>(cipherSuites.length);
		for (CipherSuite c : cipherSuites) {
			map.put((char) c.id, c);
		}
		return map;
	}

	protected void onPreData(DynByteBuf o) throws MSSException {

	}

	// endregion
	// region Solid Handshake Progress

	private CipheR preDecoder;

	@Override
	public final boolean isClientMode() {
		return false;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final int handshake(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (error != null) throw error;
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int avl = toWrite.readableBytes() + 4 - out.writableBytes();
			if (avl > 0) return of(avl);

			out.put(H_SERVER_HELLO).putMedium(toWrite.readableBytes()).put(toWrite);
			freeTmpBuffer(toWrite);
			toWrite = null;

			flag &= ~WRITE_PENDING;
			return HS_OK;
		}

		// fast-fail preventing useless waiting
		if (stage == CLIENT_HELLO && in.isReadable() &&
			(in.get(in.rIndex) != H_CLIENT_HELLO && in.get(in.rIndex) != P_ALERT))
			return error(ILLEGAL_PACKET, null, out);

		int lim = in.wIndex();
		int type = checkRcv(in);
		if (type < 0) return type;
		if (type == P_ALERT) checkAndThrowError(in);

		try {
		switch (stage) {
			case CLIENT_HELLO:
			case RETRY_KEY_EXCHANGE:
				if (type != H_CLIENT_HELLO) return error(ILLEGAL_PACKET, "", out);
				return handleClientHello(out, in);
			case PREFLIGHT_END_WAIT:
				if (type == H_PRE_DATA) return handlePreflight(out, in);
				stage = FINISH_WAIT;
			case FINISH_WAIT:
				if (type != H_FINISHED) return error(ILLEGAL_PACKET, "", out);
				return handleFinish(out, in);
		}
		} finally {
			in.rIndex = in.wIndex();
			in.wIndex(lim);
		}
		return HS_OK;
	}

	// ID client_hello
	// u4 magic
	// u1 version (反正我一直都是破坏性更改)
	// opaque[32] random
	// u2[2..2^16-2] ciphers
	// u4 support_key_exchanges_bits
	// u1 key_exchange_type
	// opaque[0..2^16-1] key_exchange_data;
	// u4 support_key_bits
	// extension[]
	private int handleClientHello(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (in.readInt() != H_MAGIC) return error(ILLEGAL_PACKET, "header", out);
		if (in.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "", out);
		int packetBegin = in.rIndex;

		byte[] sharedRandom = sharedKey = new byte[64];
		random.nextBytes(sharedRandom);
		in.read(sharedRandom,0,32);

		CipherSuite suite = null;
		CharMap<CipherSuite> map = getCipherSuiteMap();
		int len = in.readUnsignedShort();
		if (len > 1024) return error(ILLEGAL_PACKET, "cipher_suite.length", out);
		int suite_id = -1;
		for (int i = 0; i < len; i++) {
			CipherSuite mySuite = map.get((char) in.readUnsignedShort());
			if (mySuite != null) {
				suite = mySuite;
				suite_id = i;
				in.skipBytes((len-i-1) << 1);
				break;
			}
		}
		if (suite == null) return error(NEGOTIATION_FAILED, "cipher_suite", out);

		if ((in.readInt() & getSupportedKeyExchanges()) == 0) return error(NEGOTIATION_FAILED, "key_exchange", out);

		KeyAgreement ke;
		int ke_type = in.readUnsignedByte();
		if (((1 << ke_type) & getSupportedKeyExchanges()) == 0) {
			if (stage == RETRY_KEY_EXCHANGE) return error(ILLEGAL_PARAM, "hello_retry", out);
			stage = RETRY_KEY_EXCHANGE;
			in.skipBytes(in.readUnsignedShort());

			out.put(H_SERVER_HELLO).putMedium(43)
			   .put(PROTOCOL_VERSION).put(EMPTY_32).putShort(0xFFFF)
			   .putShort(4).putInt(getSupportedKeyExchanges()).putShort(0);
			return HS_OK;
		} else {
			ke = getKeyExchange(ke_type);
			ke.init(random);

			try {
				initKeyDeriver(suite, ke.readPublic(in.slice(in.readUnsignedShort())));
			} catch (Exception e) {
				return error(e, out);
			}
		}

		int supported_certificate = in.readInt();

		CharMap<DynByteBuf> extOut = new CharMap<>();
		CharMap<DynByteBuf> extIn = Extension.read(in);

		// 0-RTT
		if ((flag & ALLOW_0RTT) != 0) {
			DynByteBuf sid = extIn.remove(Extension.session);
			MSSSession s = sid == null ? null : getSessionById(sid);
			if (s == null) {
				s = new MSSSession(deriveKey("session", suite.ciphers.getKeySize()), suite.ciphers);
				extOut.put(Extension.session, generateSessionId(s));
				// 如果失败则丢弃0-RTT消息
			} else {
				extOut.put(Extension.session, null);
				preDecoder = s.ciphers.get();
				byte[] sk = s.key.clone();
				for (int i = 0; i < sk.length; i++) {
					sk[i] ^= sharedRandom[i&31];
				}
				preDecoder.setKey(sk, CipheR.DECRYPT);
			}
		}

		// Pre-shared certificate to decrease bandwidth consumption
		MSSPrivateKey cert = null;
		if (preSharedCerts != null) {
			DynByteBuf psc = extIn.remove(Extension.pre_shared_certificate);
			if (psc == null) {
				if ((flag & PSC_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate only", out);
			} else {
				int i;
				while (psc.isReadable()) {
					cert = preSharedCerts.get(i = psc.readInt());
					if (cert != null) {
						extOut.put(Extension.pre_shared_certificate, ByteList.allocate(4).putInt(i));
						break;
					}
				}
			}
		}

		if (cert == null) {
			cert = getCertificate(extIn,supported_certificate);
			if (((1 << cert.format()) & supported_certificate) == 0) return error(NEGOTIATION_FAILED, "certificateType", out);

			byte[] data = cert.publicKey();
			extOut.put(Extension.certificate, ByteList.allocate(1+data.length).put((byte) cert.format()).put(data));
		}

		if ((flag & VERIFY_CLIENT) != 0)
			extOut.put(Extension.certificate_request, ByteList.allocate(4).putInt(getSupportCertificateType()));

		processExtensions(extIn, extOut, 1);
		// ID server_hello
		// u1 version
		// opaque[32] random
		// u2 ciphers (0xFFFF means hello_retry
		// opaque[0..2^16-1] key_share_length
		// extension[] encrypted_ext (encrypt if not in retry mode)
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
		sign.update(in.slice(packetBegin, in.rIndex-packetBegin));

		int pos = ob.wIndex();
		Extension.write(extOut, ob);

		// signature of not encrypted data
		sign.update(ob);

		byte[] bb = tmp = sign.digestShared();
		try {
			bb = cert.privateCipher().doFinal(bb);
		} catch (GeneralSecurityException e) {
			return error(e, out);
		}
		ob.putShort(bb.length).put(bb);

		stage = PREFLIGHT_END_WAIT;
		encoder = suite.ciphers.get();
		decoder = suite.ciphers.get();

		encoder.setKey(deriveKey("s2c0", suite.ciphers.getKeySize()), CipheR.ENCRYPT);
		decoder.setKey(deriveKey("c2s0", suite.ciphers.getKeySize()), CipheR.DECRYPT);

		encoder.setOption("PRNG", ComboRandom.from(deriveKey("s2c0rnd",32)));
		decoder.setOption("PRNG", ComboRandom.from(deriveKey("c2s0rnd",32)));

		DynByteBuf encb = allocateTmpBuffer(encoder.getCryptSize(ob.readableBytes()));
		try {
			ob.rIndex = pos;
			encoder.crypt(ob, encb);
			ob.wIndex(pos); ob.put(encb);
			ob.rIndex = 0;
		} catch (GeneralSecurityException e) {
			return error(e, out);
		} finally {
			freeTmpBuffer(encb);
		}

		int avl = out.writableBytes() - ob.length() - 4;
		if (avl >= 0) {
			out.put(H_SERVER_HELLO).putMedium(ob.length()).put(ob);
			return HS_OK;
		} else {
			flag |= WRITE_PENDING;
			toWrite = allocateTmpBuffer(ob.length()).put(ob);
			return of(avl);
		}
	}

	// ID preflight_data
	// opaque[*] encoded_data
	private int handlePreflight(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (preDecoder != null) {
			DynByteBuf o = allocateTmpBuffer(preDecoder.getCryptSize(in.readableBytes()));
			try {
				preDecoder.crypt(in, o);
				onPreData(o);
			} catch (Exception e) {
				return error(e, out);
			} finally {
				freeTmpBuffer(o);
			}
		}
		return HS_OK;
	}

	byte[] tmp;

	// ID client_finish
	// extension[] encrypted_ext
	// opaque[signer.length] encrypted_signature
	private int handleFinish(DynByteBuf out, DynByteBuf in) throws MSSException {
		try {
			int pos = in.rIndex;
			decoder.cryptInline(in, in.readableBytes());

			CharMap<DynByteBuf> extIn = Extension.read(in);
			if ((flag & VERIFY_CLIENT) != 0) {
				DynByteBuf client_cert = extIn.remove(Extension.certificate);
				DynByteBuf cert_verify = extIn.remove(Extension.certificate_verify);
				if (client_cert == null || cert_verify == null) return error(ILLEGAL_PARAM, "certificate missing", out);

				Object o = checkCertificate(client_cert.readUnsignedByte(), client_cert);
				if (o instanceof Throwable) return error((Throwable) o, out);

				int delta = 0;
				byte[] signRemote = ((MSSPublicKey) o).publicCipher().doFinal(cert_verify.toByteArray());
				byte[] signLocal = deriveKey("client_verify", 32);
				for (int i = 0; i < signLocal.length; i++) {
					delta |= signLocal[i] ^ signRemote[i];
				}
				if (delta!=0) return error(ILLEGAL_PARAM, "cert_verify", out);
			}
			processExtensions(extIn, null, 3);

			HMAC sign = keyDeriver;
			sign.setSignKey(deriveKey("verify_c", sign.getDigestLength()));
			sign.update(EMPTY_32);
			sign.update(tmp);
			sign.update(in.slice(pos, in.rIndex-pos));

			int delta = 0;
			for (byte b : sign.digestShared()) delta |= b^in.get();
			if (delta!=0) return error(ILLEGAL_PARAM, "signature", out);
		} catch (GeneralSecurityException e) {
			return error(e, out);
		}
		stage = HS_DONE;
		return HS_OK;
	}

	// endregion
}
