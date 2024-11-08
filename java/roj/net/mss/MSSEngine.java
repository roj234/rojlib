package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.*;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.function.Supplier;

/**
 * MSS协议处理器：My Secure Socket
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public abstract class MSSEngine {
	MSSEngine() {random = new SecureRandom();}
	MSSEngine(SecureRandom rnd) {this.random = rnd;}

	final SecureRandom random;

	static CipherSuite[] defaultCipherSuites = {CipherSuite.TLS_AES_128_GCM_SHA256, CipherSuite.TLS_AES_256_GCM_SHA384, CipherSuite.TLS_CHACHA20_POLY1305_SHA256};
	CipherSuite[] cipherSuites = defaultCipherSuites;

	public static void setDefaultCipherSuites(CipherSuite[] ciphers) {
		if (ciphers.length > 1024) throw new IllegalArgumentException("ciphers.length > 1024");
		MSSEngine.defaultCipherSuites = ciphers.clone();
	}

	public final void setCipherSuites(CipherSuite[] ciphers) {
		if (ciphers.length > 1024) throw new IllegalArgumentException("ciphers.length > 1024");
		this.cipherSuites = ciphers;
	}

	public static final byte PSC_ONLY = 0x1;
	static final byte WRITE_PENDING = (byte) 128;

	byte flag, stage;

	HMAC keyDeriver;
	RCipherSpi encoder, decoder;
	byte[] sharedKey;

	public abstract void switches(int sw);
	public int switches() { return flag; }

	protected MSSKeyPair cert;
	public MSSEngine setDefaultCert(MSSKeyPair cert) {
		assertInitial();
		this.cert = cert;
		return this;
	}
	protected MSSKeyPair getCertificate(CharMap<DynByteBuf> ext, int formats) { return cert != null && ((1 << cert.format())&formats) != 0 ? cert : null; }

	public abstract void setPreSharedCertificate(IntMap<MSSPublicKey> certs);

	final void assertInitial() { if (stage != INITIAL) throw new IllegalStateException(); }

	/**
	 * 客户端模式
	 */
	public abstract boolean isClientMode();
	public final boolean isHandshakeDone() { return stage >= HS_DONE; }
	public final boolean isClosed() { return stage == HS_FAIL; }

	protected int getSupportedKeyExchanges() { return CipherSuite.ALL_KEY_EXCHANGE_TYPE; }
	protected KeyExchange getKeyExchange(int type) {
		if (type == -1) return CipherSuite.getKeyExchange(CipherSuite.KEX_ECDHE_secp384r1);
		return CipherSuite.getKeyExchange(type);
	}

	protected int getSupportCertificateType() { return CipherSuite.ALL_PUBLIC_KEY_TYPE; }
	protected MSSPublicKey checkCertificate(int type, DynByteBuf data) throws GeneralSecurityException {
		return CipherSuite.getKeyFormat(type).decode(data);
	}

	protected void processExtensions(CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int stage) {}

	protected final RCipherSpi getSessionCipher(MSSSession session, int mode) throws MSSException {
		HMAC pfKd = new HMAC(session.suite.sign.get());
		byte[] pfSk = session.key;

		RCipherSpi cipher = session.suite.cipher.get();
		try {
			cipher.init(mode,
				KDF.HKDF_expand(pfKd, pfSk, new ByteList(2).putAscii("PF"), session.suite.cipher.getKeySize()), null,
				new HKDFPRNG(pfKd, pfSk, "PF"));
		} catch (GeneralSecurityException e) {
			error(e);
		}
		return cipher;
	}

	public static final byte[] EMPTY_32 = new byte[32];

	final void initKeyDeriver(CipherSuite suite, byte[] sharedKey_pre) {
		keyDeriver = new HMAC(suite.sign.get());
		sharedKey = KDF.HKDF_expand(keyDeriver, sharedKey_pre, ByteList.wrap(sharedKey), 64);
	}
	public final byte[] deriveKey(String name, int len) {
		DynByteBuf info = heapBuffer(ByteList.byteCountUTF8(name)+2).putUTF(name);
		byte[] secret = KDF.HKDF_expand(keyDeriver, sharedKey, info, len);
		free(info);
		return secret;
	}
	public final SecureRandom getPRNG(String name) { return new HKDFPRNG(keyDeriver, sharedKey, name); }

	protected DynByteBuf heapBuffer(int capacity) { return BufferPool.buffer(false, capacity); }
	static void free(DynByteBuf buf) { if (BufferPool.isPooled(buf)) BufferPool.reserve(buf); }

	public final void close() {
		encoder = decoder = null;
		sharedKey = null;
		keyDeriver = null;
		if (toWrite != null) {
			free(toWrite);
			toWrite = null;
		}
		stage = HS_FAIL;
	}
	private void close(String reason) throws MSSException {
		close();
		throw new MSSException(reason);
	}

	public final void reset() {
		close();
		stage = INITIAL;
	}

	DynByteBuf toWrite;

	public static final int HS_PRE_DATA = -2097153, HS_OK = 0, HS_BUFFER_UNDERFLOW = -1;

	// 内部状态
	static final byte INITIAL = 0, SERVER_HELLO = 1, FINISH_WAIT = 3;
	static final byte CLIENT_HELLO = 0, RETRY_KEY_EXCHANGE = 1, PREFLIGHT_WAIT = 2;
	static final byte HS_DONE = 4, HS_FAIL = 5;

	// 数据包
	static final byte PROTOCOL_VERSION = 3;

	static final byte H_CLIENT_PACKET = 0x31, H_SERVER_PACKET = 0x32;
	public static final byte P_ALERT = 0x30, P_PREDATA = 0x34;

	// 错误类别
	public static final byte
		ILLEGAL_PACKET = 0,
		CIPHER_FAULT = 1,
		VERSION_MISMATCH = 2,
		INTERNAL_ERROR = 3,
		ILLEGAL_PARAM = 4,
		NEGOTIATION_FAILED = 6;

	public final int readPacket(DynByteBuf rx) throws MSSException {
		int siz = rx.readableBytes()-3;
		if (siz < 0) return siz;

		int pos = rx.rIndex;
		int len = rx.readShort(pos+1);
		if (siz < len) return siz-len;

		rx.rIndex = pos+3;
		rx.wIndex(rx.rIndex+len);

		int type = rx.getU(pos);
		if (type == P_ALERT) handleError(rx);
		return type;
	}
	final void handleError(DynByteBuf in) throws MSSException {
		int flag;
		byte[] signLocal;
		if (keyDeriver != null) {
			keyDeriver.setSignKey(deriveKey("alert", keyDeriver.getDigestLength()));
			DynByteBuf slice = in.slice(in.rIndex, in.readableBytes() - keyDeriver.getDigestLength());
			keyDeriver.update(slice);
			flag = 0;
			signLocal = keyDeriver.digestShared();
		} else {
			flag = 1;
			signLocal = null;
		}

		close();

		int errCode = in.readUnsignedByte();
		String errMsg = in.readUTF(in.readUnsignedByte());

		if (signLocal != null) {
			if (in.readableBytes() != signLocal.length) {
				flag = 1;
			} else {
				for (byte b : signLocal)
					flag |= b ^ in.readByte();
			}
		}

		throw new MSSException(ILLEGAL_PACKET, "对等端错误("+errCode+"): "+(flag==0?"":"unverified,")+"'"+errMsg+"'", null);
	}

	/**
	 * 进行握手操作
	 *
	 * @param tx 发送缓冲
	 * @param rx 接收的数据
	 *
	 * @return 状态 OK(0) BUFFER_OVERFLOW(>0) BUFFER_UNDERFLOW(<0)
	 */
	public abstract int handshake(DynByteBuf tx, DynByteBuf rx) throws MSSException;

	public RCipherSpi getEncoder() {
		if (encoder == null) throw new NullPointerException("不适合的状态:"+stage);
		return encoder;
	}
	public RCipherSpi getDecoder() {
		if (decoder == null) throw new NullPointerException("不适合的状态:"+stage);
		return decoder;
	}

	@Override
	public String toString() { return getClass().getSimpleName()+"{stage="+stage+"}"; }

	static int error(int code, String reason) throws MSSException { throw new MSSException(code, reason, null); }
	static int error(Throwable ex) throws MSSException { throw new MSSException(CIPHER_FAULT, "", ex); }

	public abstract int handshakeTLS13(DynByteBuf out, DynByteBuf recv) throws MSSException;

	public static Factory serverFactory() {return new Factory(false);}
	public static Factory clientFactory() {return new Factory(true);}
	public static MSSEngine client() {return new MSSEngineClient();}

	/**
	 * @author Roj233
	 * @since 2021/12/24 22:44
	 */
	public static final class Factory implements Supplier<MSSEngine> {
		public Factory(boolean client) {this.client = client;}

		private final boolean client;
		private MSSKeyPair pair;
		private int switches;
		private IntMap<MSSPublicKey> psc;

		public Factory key(KeyPair key) throws GeneralSecurityException {return key(new MSSKeyPair(key));}
		public Factory key(MSSKeyPair pair) {this.pair = pair;return this;}
		public Factory switches(int switches) {this.switches = switches;return this;}

		public Factory psc(int id, PublicKey key) {return psc(id, new MSSPublicKey(key));}
		public Factory psc(int id, MSSPublicKey key) {
			if (!client && !(key instanceof MSSKeyPair)) throw new IllegalArgumentException("Server mode psc must be private key");
			if (psc == null) psc = new IntMap<>();
			psc.putInt(id, key);
			return this;
		}

		public MSSKeyPair getKeyPair() {return pair;}

		@Override
		public MSSEngine get() {
			MSSEngine s = client ? new MSSEngineClient() : new MSSEngineServer();
			s.setDefaultCert(pair).switches(switches);
			s.setPreSharedCertificate(psc);
			return s;
		}
	}
}