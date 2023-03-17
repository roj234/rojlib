package roj.net.http.srv;

import roj.net.http.Action;

/**
 * @author Roj233
 * @since 2022/3/13 0:48
 */
public class CorsUtil {
	public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	public static final String ACCESS_CONTROL_MAX_aGE = "Access-Control-Max-Age";
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

	public static boolean isPreflightRequest(Request req) {
		return req.action() == Action.OPTIONS && req.containsKey("Origin") && req.containsKey("Access-Control-Request-Method");
	}
}
