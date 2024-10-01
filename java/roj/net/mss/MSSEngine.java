package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.HKDFPRNG;
import roj.crypt.HMAC;
import roj.crypt.KeyExchange;
import roj.crypt.RCipherSpi;
import roj.io.buf.BufferPool;
import roj.net.handler.VarintSplitter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

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
			cipher.init(Cipher.DECRYPT_MODE,
				HMAC.HKDF_expand(pfKd, pfSk, new ByteList(2).putAscii("PF"), session.suite.cipher.getKeySize()), null,
				new HKDFPRNG(pfKd, pfSk, "PF"));
		} catch (GeneralSecurityException e) {
			error(e);
		}
		return cipher;
	}

	public static final byte[] EMPTY_32 = new byte[32];

	final void initKeyDeriver(CipherSuite suite, byte[] sharedKey_pre) {
		keyDeriver = new HMAC(suite.sign.get());
		sharedKey = HMAC.HKDF_expand(keyDeriver, sharedKey_pre, ByteList.wrap(sharedKey), 64);
	}
	public final byte[] deriveKey(String name, int len) {
		DynByteBuf info = heapBuffer(ByteList.byteCountUTF8(name)+2).putUTF(name);
		byte[] secret = HMAC.HKDF_expand(keyDeriver, sharedKey, info, len);
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

	static final byte H_CLIENT_HELLO = 0x40, H_SERVER_HELLO = 0x41, H_ENCRYPTED_EXTENSION = 0x42;
	static final byte P_ALERT = 0x30, P_DATA = 0x31, P_PREDATA = 0x32;

	// 错误类别
	public static final byte
		ILLEGAL_PACKET = 0,
		CIPHER_FAULT = 1,
		VERSION_MISMATCH = 2,
		INTERNAL_ERROR = 3,
		ILLEGAL_PARAM = 4,
		NEGOTIATION_FAILED = 6;

	final int ensureWritable(DynByteBuf out, int len) {
		int size = len - out.writableBytes();
		return size > 0 ? size : 0;
	}
	final int readPacket(DynByteBuf rx) throws MSSException {
		if (!rx.isReadable()) return HS_BUFFER_UNDERFLOW;

		int pos = rx.rIndex;
		int hdr = rx.getU(pos);
		if (hdr != P_DATA) {
			int siz = rx.readableBytes()-4;
			if (siz < 0) return siz;

			int len = rx.readMedium(pos+1);
			if (siz < len) return siz-len;

			rx.rIndex += 4;
			rx.wIndex(rx.rIndex+len);

			if (hdr == P_ALERT) handleError(rx);
		} else {
			int len = VarintSplitter.readVarInt(rx, 3);
			if (len < 0) return len;
			rx.wIndex(rx.rIndex+len);
		}

		return hdr;
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

		throw new MSSException("对等端错误("+errCode+"): "+(flag==0?"":"unverified,")+"'"+errMsg+"'");
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
	/**
	 * 2024.09.14: 现已使用VLUI！不再主动拆分数据包，如果你不喜欢可以把两个密码器拿走
	 * @param in 接收缓冲
	 * @return OK(0) BUFFER_UNDERFLOW(<0)
	 */
	public int publicReadPacket(DynByteBuf in) throws MSSException {
		int type = readPacket(in);
		if (type < 0) return type;
		if (type != P_DATA) close("非法数据包");
		return 0;
	}

	/**
	 * 编码数据用于发送
	 *
	 * @param in 源缓冲
	 * @param out 发送缓冲
	 *
	 * @return 负数代表还需要至少n个字节的输出缓冲
	 */
	public int wrap(DynByteBuf in, DynByteBuf out) throws MSSException {
		if (encoder == null) throw new MSSException("不适合的状态:"+stage);

		int lim = in.wIndex();
		int w = in.readableBytes();

		int t = out.writableBytes() - 1 - VarintSplitter.getVarIntLength(w) - encoder.engineGetOutputSize(w);
		if (t < 0) return t;

		try {
			in.wIndex(in.rIndex + w);
			out.put(decoder == null ? P_PREDATA : P_DATA).putVUInt(encoder.engineGetOutputSize(w));
			encoder.cryptFinal(in, out);
		} catch (GeneralSecurityException e) {
			close("加密失败");
		} finally {
			in.wIndex(lim);
		}
		return 0;
	}

	@Override
	public String toString() { return getClass().getSimpleName()+"{stage="+stage+"}"; }

	static int error(int code, String reason) throws MSSException { throw new MSSException(code, reason, null); }
	static int error(Throwable ex) throws MSSException { throw new MSSException(CIPHER_FAULT, "", ex); }

	public abstract int handshakeTLS13(DynByteBuf out, DynByteBuf recv) throws MSSException;
}