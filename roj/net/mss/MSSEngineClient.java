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

	private static final int HAS_HELLO_RETRY = 0x8;

	@Override
	public final void switches(int sw) {
		final int AVAILABLE_SWITCHES = PSC_ONLY | ALLOW_0RTT | STREAM_DECRYPT;
		int i = sw & ~AVAILABLE_SWITCHES;
		if (i != 0) throw new IllegalArgumentException("Illegal switch:"+i);

		flag = (byte) i;
	}

	// region inheritor modifiable

	protected IntMap<MSSPublicKey> psc;

	@Override
	public void setPSC(IntMap<MSSPublicKey> keys) {
		if (stage != INITIAL) throw new IllegalStateException();
		this.psc = keys;
	}

	public void setSession(MSSSession session) {
		if (stage != INITIAL) throw new IllegalStateException();
		this.session = session;
	}

	protected MSSPrivateKey getClientCertificate(int supported) {
		return null;
	}

	// endregion
	// region Solid Handshake Progress

	private KeyAgreement keyExch;
	private CipheR preEncoder;

	public final int sendPreData(DynByteBuf i, DynByteBuf o) throws MSSException {
		if (stage >= HS_DONE) return super.wrap(i,o);

		if (preEncoder == null) return 0;

		int cipSize = preEncoder.getCryptSize(i.readableBytes());
		int size = o.writableBytes()-4-cipSize;
		if (size < 0) return size;

		o.put(H_PRE_DATA).putMedium(cipSize);
		try {
			preEncoder.crypt(i, o);
		} catch (GeneralSecurityException e) {
			throw new MSSException("cipher fault", e);
		}
		return cipSize;
	}

	@Override
	public final boolean isClientMode() {
		return true;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final int handshake(DynByteBuf out, DynByteBuf in) throws MSSException {
		if (error != null) throw error;
		if (stage == HS_DONE) return HS_OK;

		if ((flag & WRITE_PENDING) != 0) {
			int avl = toWrite.readableBytes() + 4 - out.writableBytes();
			if (avl > 0) return of(avl);
			out.put(stage==INITIAL? H_CLIENT_HELLO : H_FINISHED).putMedium(toWrite.readableBytes()).put(toWrite);

			if (stage != INITIAL) {
				freeTmpBuffer(toWrite);
				toWrite = null;
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
				// u4 support_key_exchanges_bits
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
				ob.putInt(getSupportedKeyExchanges());

				KeyAgreement ke = keyExch==null ? keyExch = getKeyExchange(-1) : keyExch;
				ke.init(random);
				ke.writePublic(ob.put(CipherSuite.getKeyAgreementId(ke.getAlgorithm())).putShort(ke.length()));

				CharMap<DynByteBuf> ext = new CharMap<>();
				if (session != null) {
					preEncoder = session.ciphers.get();
					byte[] sk = session.key.clone();
					for (int i = 0; i < sk.length; i++) {
						sk[i] ^= sharedKey[i&31];
					}
					preEncoder.setKey(sk, CipheR.ENCRYPT);
					ext.put(Extension.session, session.id);
				}
				if (psc != null) {
					ByteList tmp = ByteList.allocate(psc.size() << 2);
					for (IntMap.Entry<MSSPublicKey> entry : psc.selfEntrySet()) {
						tmp.putInt(entry.getIntKey());
					}
					ext.put(Extension.pre_shared_certificate, tmp);
				}
				processExtensions(null, ext, 0);
				Extension.write(ext, ob.putInt(getSupportCertificateType()));

				toWrite = allocateTmpBuffer(ob.readableBytes()).put(ob);
				stage = SERVER_HELLO;

				if (out.writableBytes() < ob.length() + 4) {
					flag |= WRITE_PENDING;
					return of(ob.length() + 4 - out.writableBytes());
				}
				out.put(H_CLIENT_HELLO).putMedium(ob.length()).put(ob);
				break;
			case SERVER_HELLO:
				if (in.isReadable() && (in.get(in.rIndex) != H_SERVER_HELLO && in.get(in.rIndex) != P_ALERT))
					return error(ILLEGAL_PACKET, null, out);

				int lim = in.wIndex();
				int type = checkRcv(in);
				if (type < 0) return type;
				if (type == P_ALERT) checkAndThrowError(in);

				try {
					return handleServerHello(out, in);
				} finally {
					in.rIndex = in.wIndex();
					in.wIndex(lim);
				}
		}
		return HS_OK;
	}

	// ID server_hello
	// u1 version
	// opaque[32] random
	// u2 ciphers (0xFFFF means hello_retry
	// opaque[0..2^16-1] key_share_length
	// extension[] encrypted_ext (encrypt if not in retry mode)
	// opaque[1..2^8-1] encrypted_signature
	private int handleServerHello(DynByteBuf out, DynByteBuf in) throws MSSException {
		int inBegin = in.rIndex;

		if (in.readUnsignedByte() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "", out);
		in.read(sharedKey,32,32);

		int cs_id = in.readUnsignedShort();
		if (cs_id == 0xFFFF) {
			if ((flag & HAS_HELLO_RETRY) != 0) return error(ILLEGAL_PARAM, "hello_retry", out);
			flag |= HAS_HELLO_RETRY;

			if (in.readUnsignedShort() != 4) return error(ILLEGAL_PACKET, "", out);
			int ke_avl = getSupportedKeyExchanges() & in.readInt();
			if (ke_avl == 0) return error(NEGOTIATION_FAILED, "key_exchange", out);

			int ke_id = 0;
			while ((ke_avl&1) == 0) {
				ke_avl >>>= 1;
				ke_id++;
			}

			keyExch = getKeyExchange(ke_id);
			stage = INITIAL;
			return handshake(out, in);
		}

		if (cs_id >= cipherSuites.length) return error(ILLEGAL_PARAM, "cipher_suite", out);
		CipherSuite suite = cipherSuites[cs_id];

		try {
			initKeyDeriver(suite, keyExch.readPublic(in.slice(in.readUnsignedShort())));
		} catch (Exception e) {
			return error(e, out);
		}
		keyExch = null;

		encoder = suite.ciphers.get();
		decoder = suite.ciphers.get();

		encoder.setKey(deriveKey("c2s0", suite.ciphers.getKeySize()), CipheR.ENCRYPT);
		decoder.setKey(deriveKey("s2c0", suite.ciphers.getKeySize()), CipheR.DECRYPT);

		encoder.setOption("PRNG", ComboRandom.from(deriveKey("c2s0rnd",32)));
		decoder.setOption("PRNG", ComboRandom.from(deriveKey("s2c0rnd",32)));

		CharMap<DynByteBuf> extIn;
		byte[] signRemote;

		int pos;
		try {
			decoder.cryptInline(in, in.readableBytes());
			extIn = Extension.read(in);
			pos = in.rIndex;
			signRemote = in.readBytes(in.readUnsignedShort());
		} catch (GeneralSecurityException e) {
			return error(e, out);
		}

		CharMap<DynByteBuf> extOut = new CharMap<>();

		if (extIn.containsKey(Extension.certificate_request)) {
			int supported = extIn.remove(Extension.certificate_request).readInt();
			MSSPrivateKey key = getClientCertificate(supported);
			if (key == null) return error(NEGOTIATION_FAILED, "client_certificate", out);
			byte[] data = key.publicKey();
			extOut.put(Extension.certificate, ByteList.allocate(data.length+1).put((byte) key.format()).put(data));
			try {
				extOut.put(Extension.certificate_verify, ByteList.wrap(key.privateCipher().doFinal(deriveKey("client_verify", 32))));
			} catch (GeneralSecurityException e) {
				return error(e, out);
			}
		}

		MSSPublicKey key;
		if (extIn.containsKey(Extension.pre_shared_certificate)) {
			int id = extIn.remove(Extension.pre_shared_certificate).readUnsignedShort();
			if (psc == null || !psc.containsKey(id)) return error(ILLEGAL_PARAM, "pre_shared_certificate", out);
			key = psc.get(id);
			if (extIn.containsKey(Extension.certificate)) {
				return error(ILLEGAL_PARAM, "certificate and pre_shared_certificate", out);
			}
		} else {
			if ((flag & PSC_ONLY) != 0) return error(NEGOTIATION_FAILED, "pre_shared_certificate", out);
			DynByteBuf cert_data = extIn.remove(Extension.certificate);
			if (cert_data == null) return error(ILLEGAL_PARAM, "certificate", out);
			Object o = checkCertificate(cert_data.readUnsignedByte(), cert_data);
			if (o instanceof Throwable) return error((Throwable) o, out);
			key = (MSSPublicKey) o;
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
		freeTmpBuffer(toWrite);
		toWrite = null;

		sign.update(in.slice(inBegin, pos-inBegin));
		try {
			signRemote = key.publicCipher().doFinal(signRemote);
		} catch (GeneralSecurityException e) {
			return error(e, out);
		}

		int delta = 0;
		byte[] signLocal = sign.digestShared();
		for (int i = 0; i < signLocal.length; i++) {
			delta |= signLocal[i]^signRemote[i];
		}
		if (delta != 0) return error(ILLEGAL_PARAM, "signature", out);

		if (extIn.containsKey(Extension.session)) {
			DynByteBuf sessid = extIn.remove(Extension.session);
			if (sessid.isReadable()) {
				// new session
				preEncoder = null;
				if ((flag & ALLOW_0RTT) != 0) {
					session = new MSSSession(deriveKey("session", suite.ciphers.getKeySize()), suite.ciphers);
					session.id = ByteList.wrap(sessid.toByteArray());
				}
			}
		} else {
			preEncoder = null;
		}

		ByteList ob = IOUtil.getSharedByteBuf();
		Extension.write(extOut, ob);

		sign.setSignKey(deriveKey("verify_c", sign.getDigestLength()));
		sign.update(EMPTY_32);
		sign.update(signLocal);
		sign.update(ob);
		signLocal = sign.digestShared();

		ob.put(signLocal);

		DynByteBuf encb = allocateTmpBuffer(encoder.getCryptSize(ob.readableBytes()));
		try {
			encoder.crypt(ob, encb);
			ob.clear(); ob.put(encb);
		} catch (GeneralSecurityException e) {
			freeTmpBuffer(encb);
			return error(e, out);
		}

		if (out.writableBytes() < encb.readableBytes()+4) {
			flag |= WRITE_PENDING;
			toWrite = encb;
			return of(encb.readableBytes() + 4 - out.writableBytes());
		}
		out.put(H_FINISHED).putMedium(encb.readableBytes()).put(encb);

		freeTmpBuffer(encb);

		stage = HS_DONE;
		return HS_OK;
	}

	// endregion
}
