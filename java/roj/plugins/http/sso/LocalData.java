package roj.plugins.http.sso;

import org.jetbrains.annotations.NotNull;
import roj.crypt.AES_GCM;
import roj.crypt.HMAC;
import roj.crypt.MySaltedHash;
import roj.net.http.server.Request;
import roj.util.Helpers;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2024/7/8 0008 6:38
 */
final class LocalData {
	final SecureRandom srnd = new SecureRandom();
	final MySaltedHash hasher = MySaltedHash.hasher(srnd);
	HMAC hmac;
	final AES_GCM aesGcm = new AES_GCM();

	@NotNull
	static LocalData get(Request req) {
		var o = (LocalData) req.localCtx().get("xsso:data");
		if (o == null) req.localCtx().put("xsso:data", o = new LocalData());
		return o;
	}

	public LocalData() {
		try {
			hmac = TOTP.createHMAC();
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}
}