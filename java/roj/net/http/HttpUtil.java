package roj.net.http;

import roj.net.http.server.Request;

import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/24 0024 2:01
 */
public class HttpUtil {
	// region CORS
	public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	public static final String ACCESS_CONTROL_MAX_aGE = "Access-Control-Max-Age";
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

	public static boolean isCORSPreflight(Request req) {
		return req.action() == Action.OPTIONS && req.containsKey("Origin") && req.containsKey("Access-Control-Request-Method");
	}
	// endregion

	static final Pattern pa = Pattern.compile("(up.browser|up.link|mmp|symbian|smartphone|midp|wap|phone|iphone|ipad|ipod|android|xoom)", Pattern.CASE_INSENSITIVE);

	public static boolean is_wap(Request req) {
		String ua = req.get("user-agent");
		if (ua != null && pa.matcher(ua).matches()) return true;

		return req.getField("accept").contains("application/vnd.wap.xhtml+xml");
	}
}