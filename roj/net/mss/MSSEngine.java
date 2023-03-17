package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.CipheR;
import roj.crypt.HMAC;
import roj.crypt.KeyAgreement;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.nio.charset.StandardCharsets;
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

	static CipherSuite[] defaultCipherSuites = {CipherSuites.AESGCM_SHA256,CipherSuites.XCHACHA20POLY1305_SHA256};
	CipherSuite[] cipherSuites = defaultCipherSuites;

	public static void setDefaultCipherSuites(CipherSuite[] ciphers) {
		if (ciphers.length > 1024) throw new IllegalArgumentException("ciphers.length > 1024");
		MSSEngine.defaultCipherSuites = ciphers.clone();
	}

	public final void setCipherSuites(CipherSuite[] ciphers) {
		if (ciphers.length > 1024) throw new IllegalArgumentException("ciphers.length > 1024");
		this.cipherSuites = ciphers;
	}

	public static final byte PSC_ONLY = 0x1, ALLOW_0RTT = 0x2, STREAM_DECRYPT = 0x4;
	static final int WRITE_PENDING = 0x80;

	byte flag, stage;
	HMAC keyDeriver;
	int bufferSize;
	CipheR encoder, decoder;
	byte[] sharedKey;
	protected MSSSession session;
	MSSException error;

	public abstract void switches(int sw);
	public int switches() {
		return flag;
	}

	/**
	 * 客户端模式
	 */
	public abstract boolean isClientMode();

	public final boolean isClosed() {
		return stage == HS_FAIL;
	}

	public abstract void setPSC(IntMap<MSSPublicKey> keys);

	/**
	 * 关闭引擎
	 * 若reason!=null会在下次调用wrap时向对等端发送关闭消息
	 */
	public final void close(String reason) {
		if (stage > HS_DONE) return;

		endCipher();
		stage = HS_FAIL;

		if (reason != null) {
			byte[] r = reason.replaceAll("[^a-zA-Z \\-_.]", "").getBytes(StandardCharsets.UTF_8);
			toWrite = allocateTmpBuffer(r.length+6).put(P_ALERT).putShort(2+r.length).put(INTERNAL_ERROR).put(r);
		}
	}

	private void endCipher() {
		encoder = decoder = null;
		sharedKey = null;
	}

	public final void reset() {
		endCipher();
		this.stage = 0;
		this.sharedKey = null;
		this.toWrite = null;
		this.flag = 0;
		this.session = null;
		this.keyDeriver = null;
		this.error = null;
	}

	public final boolean isHandshakeDone() {
		return stage >= HS_DONE;
	}

	public final byte[] getKey(String name, int length) {
		if (stage != HS_DONE) throw new IllegalStateException();
		return deriveKey(name, length);
	}

	protected int getSupportedKeyExchanges() {
		return (1 << CipherSuite.KEX_DH) | (1 << CipherSuite.KEX_ECDH_SECP384R1);
	}
	protected KeyAgreement getKeyExchange(int type) {
		if (type == -1) return CipherSuite.getKeyAgreement(CipherSuite.KEX_ECDH_SECP384R1);
		return CipherSuite.getKeyAgreement(type);
	}

	protected int getSupportCertificateType() {
		return CipherSuite.ALL_CERTIFICATE_TYPE;
	}
	protected Object checkCertificate(int type, DynByteBuf data) {
		try {
			MSSPublicKey key = CipherSuite.getPublicKeyFactory(type).decode(data.toByteArray());
			if (key instanceof JCertificateKey) ((JCertificateKey) key).verify(JCertificateKey.getDefault());
			return key;
		} catch (Exception e) {
			return e;
		}
	}
	protected void processExtensions(CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int i) {

	}
	public static final byte[] EMPTY_32 = new byte[32];

	final void initKeyDeriver(CipherSuite suite, byte[] sharedKey_pre) {
		keyDeriver = new HMAC(suite.sign.get());
		sharedKey = HMAC.HKDF_expand(keyDeriver, sharedKey_pre, ByteList.wrap(sharedKey), 64);
	}
	protected final byte[] deriveKey(String name, int len) {
		DynByteBuf info = allocateTmpBuffer(256);
		info.putShort(1145141919+name.length())
			.putAscii("mss_").putAscii(name);

		byte[] secret = HMAC.HKDF_expand(keyDeriver,sharedKey,info,len);
		freeTmpBuffer(info);
		return secret;
	}

	protected DynByteBuf allocateTmpBuffer(int capacity) {
		return BufferPool.localPool().buffer(false, capacity);
	}
	protected void freeTmpBuffer(DynByteBuf buf) {
		BufferPool.localPool().reserve(buf);
	}

	DynByteBuf toWrite;

	public static final int HS_OK = 0, HS_BUFFER_OVERFLOW = -1, HS_BUFFER_UNDERFLOW = -2;

	// 内部状态
	static final int INITIAL = 0, SERVER_HELLO = 1, FINISH_WAIT = 3;
	static final int CLIENT_HELLO = 0, RETRY_KEY_EXCHANGE = 1, PREFLIGHT_END_WAIT = 2;
	static final int HS_DONE = 4, HS_FAIL = 5;

	// 数据包
	static final byte PROTOCOL_VERSION = 20;

	static final int H_MAGIC = 0x53534E43;
	static final byte H_CLIENT_HELLO = 0x40, H_SERVER_HELLO = 0x41, H_PRE_DATA = 0x42, H_FINISHED = 0x43;
	static final byte P_ALERT = 0x30, P_DATA = 0x31, P_CHANGE_KEY = 0x32;

	static final byte
		ILLEGAL_PACKET = 0,
		CIPHER_FAULT = 1,
		VERSION_MISMATCH = 2,
		INTERNAL_ERROR = 3,
		ILLEGAL_PARAM = 4,
		NEGOTIATION_FAILED = 6;

	public final int getBufferSize() {
		if (bufferSize == 0) throw new IllegalStateException();
		int cap = bufferSize;
		bufferSize = 0;
		return cap;
	}
	final int of(int rem) {
		bufferSize = rem;
		return HS_BUFFER_OVERFLOW;
	}
	final int checkRcv(DynByteBuf rcv) {
		if (!rcv.isReadable()) {
			bufferSize = 1;
			return HS_BUFFER_UNDERFLOW;
		}
		int pos = rcv.rIndex;
		int lenLen = rcv.getU(pos) >= 0x40 ? 4 : 3;

		int siz = rcv.readableBytes()-lenLen;
		if (siz < 0) {
			bufferSize = -siz;
			return HS_BUFFER_UNDERFLOW;
		}

		int len = lenLen==3 ? rcv.readUnsignedShort(pos+1) : rcv.readMedium(pos+1);
		if (siz < len) {
			bufferSize = len-siz;
			return HS_BUFFER_UNDERFLOW;
		}

		rcv.rIndex += lenLen;
		rcv.wIndex(rcv.rIndex+len);
		return rcv.getU(pos);
	}

	/**
	 * 进行握手操作
	 *
	 * @param snd 预备发送的数据缓冲区
	 * @param rcv 接收到的数据
	 *
	 * @return 状态 HS_OK HS_BUFFER_OVERFLOW HS_BUFFER_UNDERFLOW
	 */
	public abstract int handshake(DynByteBuf snd, DynByteBuf rcv) throws MSSException;

	/**
	 * 解码收到的数据
	 *
	 * @param i 接收缓冲
	 * @param o 目的缓冲
	 *
	 * @return 0ok，正数代表还需要n个字节的输入，负数代表还需要n个字节的输出
	 */
	public final int unwrap(DynByteBuf i, DynByteBuf o) throws MSSException {
		if (stage != HS_DONE) {
			if (error != null) throw error;
			throw new MSSException("不适合的状态:" + stage);
		}

		int lim = i.wIndex();

		int type = checkRcv(i);
		if (type < 0) return -type;

		try {
			int res = unwrap(type, i, o);
			if (res != 0) i.rIndex -= 3;
			return res;
		} finally {
			i.wIndex(lim);
		}
	}

	private int unwrap(int type, DynByteBuf in, DynByteBuf out) throws MSSException {
		switch (type) {
			case P_DATA:
				if (in.readableBytes() >= 18000) _close("数据包过大");

				int size = out.writableBytes() - decoder.getCryptSize(in.readableBytes());
				if (size < 0) return size;

				int pos = out.wIndex();
				try {
					decoder.crypt(in, out);
				} catch (GeneralSecurityException e) {
					_close("解密失败");
				}
				break;
			case P_CHANGE_KEY: _close("ChangeKeySpec not implemented yet"); break;
			case P_ALERT: checkAndThrowError(in); break;
			default: _close("illegal_packet");
		}
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
		if (stage != HS_DONE) {
			if (stage == HS_FAIL && toWrite != null) {
				int t = out.writableBytes() - toWrite.readableBytes();
				if (t < 0) return t;
				out.put(toWrite);
				freeTmpBuffer(toWrite);
				toWrite = null;
				return 0;
			}
			if (error != null) throw error;
			throw new MSSException("不适合的状态:" + stage);
		}

		int lim = in.wIndex();

		// 嗯，就是人工限制, 2^14
		int w = Math.min(in.readableBytes(), 16384);

		int t = out.writableBytes() -4 -w;
		if (t < 0) return t;

		try {
			in.wIndex(in.rIndex + w);
			out.put(P_DATA).putShort(encoder.getCryptSize(w));
			encoder.crypt(in, out);
		} catch (GeneralSecurityException e) {
			_close("加密失败");
		} finally {
			in.wIndex(lim);
		}
		return 0;
	}

	private void _close(String reason) throws MSSException {
		close(reason);
		error = new MSSException(reason);
		throw error;
	}

	@Override
	public String toString() {
		return "MSSEngine{" + "flag=" + flag + ", stage=" + stage + ", session=" + session + ", error=" + error + '}';
	}

	// region Error

	final void checkAndThrowError(DynByteBuf in) throws MSSException {
		endCipher();
		stage = HS_FAIL;

		int flag;
		byte[] signLocal;
		if (keyDeriver != null && sharedKey != null) {
			keyDeriver.setSignKey(deriveKey("alert", keyDeriver.getDigestLength()));
			keyDeriver.update(in.slice(in.rIndex, 2 + in.getU(in.rIndex + 1)));
			flag = 0;
			signLocal = keyDeriver.digestShared();
		} else {
			flag = 1;
			signLocal = null;
		}

		int errCode = in.readUnsignedByte();
		String errMsg = in.readUTF(in.readUnsignedByte());

		if (signLocal != null) {
			if (in.readableBytes() < signLocal.length) {
				flag = 1;
			} else {
				for (int i = 0; i < signLocal.length; i++) {
					flag |= signLocal[i] ^ in.get();
				}
			}
		}

		throw new MSSException("对等端错误: {trustable="+(flag==0)+",code="+errCode+",msg='"+errMsg+"'}");
	}

	final int error(int code, String reason, DynByteBuf snd) throws MSSException {
		stage = HS_FAIL;

		MSSException e = error = new MSSException(code + ": " + reason);
		if (reason == null) {
			close(null);
			throw e;
		}

		byte[] data = reason.getBytes(StandardCharsets.UTF_8);
		if (snd.capacity() < 6 + data.length) {
			close(null);
			throw e;
		}
		snd.put(P_ALERT).putShort(2+data.length).put((byte) code).put((byte) data.length).put(data);
		return HS_OK;
	}

	final int error(Throwable ex, DynByteBuf snd) throws MSSException {
		stage = HS_FAIL;

		error = new MSSException("cipher fault", ex);
		if (snd.capacity() < 3) throw error;

		snd.clear();
		snd.put(P_ALERT).putShort(2).put(CIPHER_FAULT).put((byte) 0);
		return HS_OK;
	}

	// endregion
}
