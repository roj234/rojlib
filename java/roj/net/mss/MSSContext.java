package roj.net.mss;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.crypt.KeyExchange;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2024/11/20 0020 23:39
 */
public class MSSContext {
	private static MSSContext DEFAULT_CONTEXT = new MSSContext();
	public static MSSContext getDefault() {return DEFAULT_CONTEXT;}
	public static void setDefault(MSSContext context) {DEFAULT_CONTEXT = context;}

	public static final byte PRESHARED_ONLY = 0x1, VERIFY_CLIENT = 0x2;

	protected SecureRandom getSecureRandom() {return new SecureRandom();}

	protected byte flags;
	public MSSContext setFlags(byte flags) {this.flags = flags;return this;}

	public static CharMap<CipherSuite> defaultCipherSuites = new CharMap<>();
	static {
		CipherSuite[] defaults = {CipherSuite.TLS_AES_128_GCM_SHA256, CipherSuite.TLS_AES_256_GCM_SHA384, CipherSuite.TLS_CHACHA20_POLY1305_SHA256};
		for (var suite : defaults) {
			defaultCipherSuites.put((char) suite.id, suite);
		}
	}
	protected CharMap<CipherSuite> cipherSuites = defaultCipherSuites;
	public CharMap<CipherSuite> getCipherSuites() {return cipherSuites;}
	public MSSContext setCipherSuites(CipherSuite suite1, CipherSuite... suiteN) {
		this.cipherSuites = new CharMap<>(suiteN.length+1);
		this.cipherSuites.put((char) suite1.id, suite1);
		for (var suite : suiteN) {
			this.cipherSuites.put((char) suite.id, suite);
		}
		return this;
	}
	public MSSContext setCipherSuites(CharMap<CipherSuite> cipherSuites) {this.cipherSuites = cipherSuites;return this;}

	protected MSSKeyPair cert;
	public MSSContext setCertificate(MSSKeyPair cert) {this.cert = cert;return this;}
	/**
	 *
	 * @param ext choose certificate via extension
	 * @param formats allow formats
	 * @return certificate
	 */
	protected MSSKeyPair getCertificate(Object ctx, CharMap<DynByteBuf> ext, int formats) throws MSSException { return cert != null && ((1 << cert.format())&formats) != 0 ? cert : null; }

	protected IntMap<MSSKeyPair> preSharedCerts;
	protected IntMap<MSSKeyPair> getPreSharedCerts() {return preSharedCerts;}

	protected int defaultKeyExchange = CipherSuite.KEX_ECDHE_secp384r1;
	protected int getSupportedKeyExchanges() {return CipherSuite.ALL_KEY_EXCHANGE_TYPE;}
	protected KeyExchange getKeyExchange(int type) {
		if (type == -1) type = defaultKeyExchange;
		return CipherSuite.getKeyExchange(type);
	}

	protected int getSupportCertificateType() {return CipherSuite.ALL_PUBLIC_KEY_TYPE;}
	protected MSSPublicKey checkCertificate(Object ctx, int type, DynByteBuf data, boolean isServerCert) throws MSSException, GeneralSecurityException {
		return CipherSuite.getKeyFormat(type).decode(data);
	}

	protected String alpn, serverName;
	protected CharMap<DynByteBuf> handshakeExtensions;
	public MSSContext setServerName(String serverName) {this.serverName = serverName;return this;}
	public MSSContext setALPN(String alpn) {this.alpn = alpn;return this;}
	public MSSContext addExtension(int id, DynByteBuf data) {
		if (handshakeExtensions == null) handshakeExtensions = new CharMap<>();
		handshakeExtensions.put((char)id, data);
		return this;
	}
	/**
	 *
	 * @param stage Enum[BEFORE_CLIENT_HELLO, HANDLE_CLIENT_HELLO, HANDLE_SERVER_HELLO]
	 */
	protected void processExtensions(Object ctx, CharMap<DynByteBuf> extIn, CharMap<DynByteBuf> extOut, int stage) throws MSSException {
		if (stage == 0) {
			if (handshakeExtensions != null) extOut.putAll(handshakeExtensions);
			if (serverName != null) extOut.put(Extension.server_name, new ByteList().putAscii(serverName));
			if (alpn != null) extOut.put(Extension.application_layer_protocol, new ByteList().putAscii(alpn));
		}
		if (stage == 1) {
			if (handshakeExtensions != null) extOut.putAll(handshakeExtensions);
			if (alpn != null) {
				var buf = extIn.remove(Extension.application_layer_protocol);
				if (buf == null || !alpn.equals(buf.readAscii(buf.readableBytes())))
					throw new MSSException(MSSEngine.NEGOTIATION_FAILED, "alpn", null);
			}
		}
	}

	protected DynByteBuf buffer(int capacity) {return BufferPool.buffer(false, capacity);}

	/**
	 * 如果想使用session机制，并且没有session，需要在0阶段返回key=null, id=empty(或不存在)的session
	 * @param id null on stage 0
	 * @param stage Enum[CLIENT_GET, SERVER_GET, CLIENT_SET]
	 */
	protected synchronized MSSSession getOrCreateSession(Object context, DynByteBuf id, int stage) throws MSSException {return null;}

	public final MSSEngine serverEngine() {
		if (cert == null && getClass() == MSSContext.class) throw new IllegalArgumentException("未设置服务器证书");
		return new MSSEngineServer(this);
	}
	public final MSSEngine clientEngine() {return new MSSEngineClient(this);}
}
