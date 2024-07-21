package roj.plugins.http.sso;

import org.jetbrains.annotations.NotNull;
import roj.crypt.HMAC;
import roj.crypt.MySaltedHash;
import roj.net.http.server.HttpCache;
import roj.net.http.server.Request;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2024/7/8 0008 6:38
 */
final class LocalData {
	final SecureRandom srnd = new SecureRandom();
	final MySaltedHash hasher = MySaltedHash.hasher(srnd);
	final HMAC hmac = new HMAC(HttpCache.getInstance().sha1());
	long localNonce;

	@NotNull
	static LocalData get(Request req) {
		var o = (LocalData) req.localCtx().get("xsso:data");
		if (o == null) req.localCtx().put("xsso:data", o = new LocalData());
		return o;
	}
}