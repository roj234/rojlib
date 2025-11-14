package roj.net.mss;

import roj.crypt.*;
import roj.io.BufferPool;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * @author Roj233
 * @since 2025/08/04 17:28
 */
public abstract class TLSEngine {
	TLSEngine(MSSContext config, int allowedFlags) {
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

	protected final RCipher getSessionCipher(MSSSession session, boolean encrypt) throws MSSException {
		HMAC pfKd = new HMAC(session.suite.sign.get());
		byte[] pfSk = session.key;

		RCipher cipher = session.suite.cipher.get();
		try {
			cipher.init(encrypt,
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
			BufferPool.reserve(toWrite);
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
	static final byte[] HELLO_RETRY_REQUEST = IOUtil.decodeHex("CF21AD74E59A6111BE1D8C021E65B891C2A211167ABB8C5E079E09E2C8A8339C");
	static final byte[] DOWNGRADE_12 = IOUtil.decodeHex("444F574E47524401");
	static final byte[] DOWNGRADE_11 = IOUtil.decodeHex("444F574E47524400");
	static final byte[] FAKE_SESSION_ID = TextUtil.hex2bytes("e0e1e2e3 e4e5e6e7 e8e9eaeb ecedeeef f0f1f2f3 f4f5f6f7 f8f9fafb fcfdfeff");

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

	// 错误类别
	public static final byte
		ILLEGAL_PACKET = 0,
		CIPHER_FAULT = 1,
		VERSION_MISMATCH = 2,
		INTERNAL_ERROR = 3,
		ILLEGAL_PARAM = 4,
		NEGOTIATION_FAILED = 6;

	public final int readPacket(DynByteBuf rx) throws MSSException {
		int siz = rx.readableBytes()-5;
		if (siz < 0) return siz;

		int pos = rx.rIndex;
		int version = rx.getUnsignedShort(pos+1);
		if (version != 0x0303) return error(VERSION_MISMATCH, "TLS1.3 server");

		int len = rx.getUnsignedShort(pos+3);
		if (siz < len) return siz-len;

		rx.rIndex = pos+3;
		rx.wIndex(rx.rIndex+len);

		int type = rx.getUnsignedByte(pos);
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

	static byte[] HKDF_Expand_Label(MessageAuthenticCode mac, byte[] key, String label, byte[] ctx) {
		var add = new ByteList().putShort(48).putShort(label.length()+6).putAscii("tls13 ").putAscii(label).put(ctx.length).put(ctx);
		return CryptoFactory.HKDF_expand(mac, key, add, 48);
	}
}