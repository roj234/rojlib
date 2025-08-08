package roj.net.mss;

import org.jetbrains.annotations.Contract;
import roj.crypt.CryptoFactory;
import roj.crypt.HKDFPRNG;
import roj.crypt.HMAC;
import roj.crypt.RCipher;
import roj.io.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * MSS协议处理器：My Secure Socket
 *
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public abstract sealed class MSSEngine permits MSSEngineClient, MSSEngineServer {
	MSSEngine(MSSContext config, int allowedFlags) {
		this.config = config;
		this.random = config.getSecureRandom();
		this.flag = (byte) (config.flags & allowedFlags);
	}

	public Object context;
	final MSSContext config;
	final SecureRandom random;
	public MSSContext config() {return config;}

	static final byte WRITE_PENDING = (byte) 128;
	byte flag, stage;

	HMAC keyDeriver;
	RCipher encoder, decoder;
	byte[] sharedKey;

	/**
	 * 客户端模式
	 */
	public abstract boolean isClient();
	public final boolean isHandshakeDone() {return stage >= HS_DONE;}
	public abstract boolean maySendMessage();
	public final boolean isClosed() {return stage == HS_FAIL;}

	protected final RCipher getSessionCipher(MSSSession session, int mode) throws MSSException {
		HMAC pfKd = new HMAC(session.suite.sign.get());
		byte[] pfSk = session.key;

		RCipher cipher = session.suite.cipher.get();
		try {
			cipher.init(mode,
				CryptoFactory.HKDF_expand(pfKd, pfSk, new ByteList(2).putAscii("PF"), session.suite.cipher.getKeySize()), null,
				new HKDFPRNG(pfKd, pfSk, "PF"));
		} catch (GeneralSecurityException e) {
			error(e);
		}
		return cipher;
	}

	public static final byte[] EMPTY_32 = new byte[32];
	final void initKeyDeriver(CipherSuite suite, byte[] sharedKey_pre) {
		keyDeriver = new HMAC(suite.sign.get());
		sharedKey = CryptoFactory.HKDF_expand(keyDeriver, sharedKey_pre, ByteList.wrap(sharedKey), 64);
	}
	public final byte[] deriveKey(String name, int len) {
		var info = config.buffer(ByteList.byteCountUTF8(name)+2).putUTF(name);
		byte[] secret = CryptoFactory.HKDF_expand(keyDeriver, sharedKey, info, len);
		BufferPool.reserve(info);
		return secret;
	}
	public final SecureRandom getPRNG(String name) { return new HKDFPRNG(keyDeriver, sharedKey, name); }

	public final void close() {
		encoder = decoder = null;
		sharedKey = null;
		keyDeriver = null;
		if (toWrite != null) {
			toWrite.release();
			toWrite = null;
		}
		stage = HS_FAIL;
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
			keyDeriver.init(deriveKey("alert", keyDeriver.getDigestLength()));
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

	public RCipher getEncoder() {
		if (encoder == null) throw new NullPointerException("不适合的状态:"+stage);
		return encoder;
	}
	public RCipher getDecoder() {
		if (decoder == null) throw new NullPointerException("不适合的状态:"+stage);
		return decoder;
	}

	@Override
	public String toString() { return getClass().getSimpleName()+"{stage="+stage+"}"; }

	@Contract("_,_ -> fail")
	static int error(int code, String reason) throws MSSException {
		if (reason == null) {
			switch (code) {
				case ILLEGAL_PACKET -> reason = "ILLEGAL_PACKET";
				case CIPHER_FAULT -> reason = "CIPHER_FAULT";
				case VERSION_MISMATCH -> reason = "VERSION_MISMATCH";
				case INTERNAL_ERROR -> reason = "INTERNAL_ERROR";
				case ILLEGAL_PARAM -> reason = "ILLEGAL_PARAM";
				case NEGOTIATION_FAILED -> reason = "NEGOTIATION_FAILED";
				default -> throw new IllegalArgumentException();
			}
		}
		throw new MSSException(code, reason, null);
	}
	static int error(Throwable ex) throws MSSException { throw new MSSException(CIPHER_FAULT, "CIPHER_FAULT", ex); }
}