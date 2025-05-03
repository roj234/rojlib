package roj.plugins.web.sso;

import org.jetbrains.annotations.NotNull;
import roj.crypt.HMAC;
import roj.crypt.MySaltedHash;
import roj.http.server.HSConfig;
import roj.http.server.Request;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2024/7/8 6:38
 */
final class LocalData {
	final SecureRandom srnd = new SecureRandom();
	final MySaltedHash hasher = MySaltedHash.hasher(srnd);
	final HMAC hmac = new HMAC(HSConfig.getInstance().sha1());
	final byte[] tmpNonce = new byte[8];
	long localNonce;

	@NotNull
	static LocalData get(Request req) {
		if (req == null) return new LocalData();

		var o = (LocalData) req.threadLocal().get("xsso:data");
		if (o == null) req.threadLocal().put("xsso:data", o = new LocalData());
		return o;
	}
}