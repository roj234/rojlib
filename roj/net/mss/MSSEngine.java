package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.HKDFPRNG;
import roj.crypt.HMAC;
import roj.crypt.KeyAgreement;
import roj.crypt.RCipherSpi;
import roj.io.buf.BufferPool;
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

	static CipherSuite[] defaultCipherSuites = {CipherSuites.TLS_AES_128_GCM_SHA256,CipherSuites.TLS_AES_256_GCM_SHA384,CipherSuites.TLS_CHACHA20_POLY1305_SHA256};
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
	static final int WRITE_PENDING = 0x80;

	byte flag, stage;

	HMAC keyDeriver;
	RCipherSpi encoder, decoder;
	byte[] sharedKey;

	public abstract void switches(int sw);
	public int switches() { return flag; }

	protected MSSPrivateKey cert;
	public MSSEngine setDefaultCert(MSSPrivateKey cert) {
		assertInitial();
		this.cert = cert;
		return this;
	}
	protected MSSPrivateKey getCertificate(CharMap<DynByteBuf> ext, int formats) { return cert != null && ((1 << cert.format())&formats) != 0 ? cert : null; }

	public abstract void setPreSharedCertificate(IntMap<MSSPublicKey> certs);

	final void assertInitial() { if (stage != INITIAL) throw new IllegalStateException(); }

	/**
	 * 客户端模式
	 */
	public abstract boolean isClientMode();
	public final boolean isHandshakeDone() { return stage >= HS_DONE; }
	public final boolean isClosed() { return stage == HS_FAIL; }

	protected int getSupportedKeyExchanges() { return (1 << CipherSuite.KEX_DHE_ffdhe2048) | (1 << CipherSuite.KEX_ECDHE_secp384r1); }
	protected KeyAgreement getKeyExchange(int type) {
		if (type == -1) return CipherSuite.getKeyAgreement(CipherSuite.KEX_ECDHE_secp384r1);
		return CipherSuite.getKeyAgreement(type);
	}

	protected int getSupportCertificateType() { return CipherSuite.ALL_CERTIFICATE_TYPE; }
	protected Object checkCertificate(int type, DynByteBuf data) {
		try {
			MSSPublicKey key = CipherSuite.getPublicKeyFactory(type).decode(data.toByteArray());
			if (key instanceof JCertificateKey) ((JCertificateKey) key).verify(JCertificateKey.getDefault());
			return key;
		} catch (Exception e) {
			return e;
		}
	}

	protected void processExtensions(CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int stage) {}

	protected final RCipherSpi getSessionCipher(MSSSession session, int mode) throws MSSException {
		HMAC pfKd = new HMAC(session.suite.sign.get());
		byte[] pfSk = session.key;

		RCipherSpi cipher = session.suite.ciphers.get();
		try {
			cipher.init(Cipher.DECRYPT_MODE,
				HMAC.HKDF_expand(pfKd, pfSk, new ByteList(2).putAscii("PF"), session.suite.ciphers.getKeySize()), null,
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
		DynByteBuf info = allocateTmpBuffer(ByteList.byteCountUTF8(name)+2).putUTF(name);
		byte[] secret = HMAC.HKDF_expand(keyDeriver, sharedKey, info, len);
		freeTmpBuffer(info);
		return secret;
	}
	public final SecureRandom getPRNG(String name) { return new HKDFPRNG(keyDeriver, sharedKey, name); }

	protected DynByteBuf allocateTmpBuffer(int capacity) {
		return BufferPool.localPool().buffer(false, capacity);
	}
	protected void freeTmpBuffer(DynByteBuf buf) {
		BufferPool.localPool().reserve(buf);
	}

	public final void close() {
		encoder = decoder = null;
		sharedKey = null;
		keyDeriver = null;
		if (toWrite != null) {
			freeTmpBuffer(toWrite);
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

	public static final int HS_OK = 0, HS_BUFFER_UNDERFLOW = -1;

	// 内部状态
	static final int INITIAL = 0, SERVER_HELLO = 1, FINISH_WAIT = 3;
	static final int CLIENT_HELLO = 0, RETRY_KEY_EXCHANGE = 1, PREFLIGHT_END_WAIT = 2;
	static final int HS_DONE = 4, HS_FAIL = 5;

	// 数据包
	static final byte PROTOCOL_VERSION = 21;

	static final int H_MAGIC = 0x53534E43;
	static final byte H_CLIENT_HELLO = 0x40, H_SERVER_HELLO = 0x41, H_ENCRYPTED_EXTENSION = 0x42;
	static final byte P_ALERT = 0x30, P_DATA = 0x31, P_PREDATA = 0x32;

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
		int lenHdr = hdr >= 0x40 ? 4 : 3;

		int siz = rx.readableBytes()-lenHdr;
		if (siz < 0) return siz;

		int len = lenHdr==3 ? rx.readUnsignedShort(pos+1) : rx.readMedium(pos+1);
		if (siz < len) return siz-len;

		rx.rIndex += lenHdr;
		rx.wIndex(rx.rIndex+len);

		if (hdr == P_ALERT) handleError(rx);
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
			if (in.readableBytes() < signLocal.length) {
				flag = 1;
			} else {
				for (byte b : signLocal)
					flag |= b ^ in.get();
			}
		}

		throw new MSSException("对等端错误: {trustable="+(flag==0)+",code="+errCode+",msg='"+errMsg+"'}");
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

	/**
	 * 解码收到的数据
	 *
	 * @param in 接收缓冲
	 * @param out 目的缓冲
	 *
	 * @return 状态 OK(0) BUFFER_OVERFLOW(>0) BUFFER_UNDERFLOW(<0)
	 */
	public final int unwrap(DynByteBuf in, DynByteBuf out) throws MSSException {
		if (decoder == null) throw new MSSException("不适合的状态:" + stage);

		int lim = in.wIndex();

		boolean first = true;
		while (true) {
			int type = readPacket(in);
			if (type < 0) return first ? type : 0;
			first = false;

			try {
				if (type == P_DATA) {
					if (in.readableBytes() >= 32768) close("数据包过大");

					int size = decoder.engineGetOutputSize(in.readableBytes()) - out.writableBytes();
					if (size > 0) {
						in.rIndex -= 3;
						return size;
					}

					try {
						decoder.cryptFinal(in, out);
					} catch (GeneralSecurityException e) {
						error(e);
						close("解密失败");
					}
				} else {
					close("illegal_packet");
				}
			} finally {
				in.wIndex(lim);
			}
		}
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
		if (encoder == null) throw new MSSException("不适合的状态:" + stage);

		int lim = in.wIndex();

		int w = Math.min(in.readableBytes(), 32767);

		int t = out.writableBytes() - 3 - encoder.engineGetOutputSize(w);
		if (t < 0) return t;

		try {
			in.wIndex(in.rIndex + w);
			out.put(decoder == null ? P_PREDATA : P_DATA).putShort(encoder.engineGetOutputSize(w));
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

	public abstract int handshakeSSL(DynByteBuf out, DynByteBuf recv) throws MSSException;
}
