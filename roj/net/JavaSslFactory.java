package roj.net;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2021/2/5 0:33
 */
public class JavaSslFactory implements Supplier<SSLEngine> {
	final SSLContext context;
	final SslConfig config;

	public JavaSslFactory(SSLContext context, SslConfig config) {
		this.context = context;
		this.config = config;
	}

	public static JavaSslFactory getSslFactory(SslConfig cfg) throws IOException, GeneralSecurityException {
		SSLContext context = SecureUtil.getSslContext(cfg.getPkPath(), cfg.getCaPath(), cfg.getPasswd(), cfg.isServerSide());
		return new JavaSslFactory(context, cfg);
	}

	public static JavaSslFactory getClientDefault() throws NoSuchAlgorithmException {
		return new JavaSslFactory(SSLContext.getDefault(), null);
	}

	public SSLEngine get() {
		SSLEngine engine = context.createSSLEngine();
		if (config == null) {
			engine.setNeedClientAuth(false);
			engine.setUseClientMode(true);
		} else {
			engine.setUseClientMode(!config.isServerSide());
			engine.setNeedClientAuth(config.isNeedClientAuth());
		}
		return engine;
	}
}
