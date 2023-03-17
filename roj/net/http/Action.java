package roj.net.http;

/**
 * @author Roj234
 * @since 2020/11/28 20:17
 */
public final class Action {
	public static final int MAY_BODY = 0b10110;
	public static final int GET = 0, POST = 1, PUT = 2, HEAD = 3, DELETE = 4, OPTIONS = 5, TRACE = 6, CONNECT = 7;

	public static int valueOf(String name) {
		if (name == null) return -1;
		switch (name) {
			case "GET": return GET;
			case "POST": return POST;
			case "PUT": return PUT;
			case "HEAD": return HEAD;
			case "DELETE": return DELETE;
			case "OPTIONS": return OPTIONS;
			case "TRACE": return TRACE;
			case "CONNECT": return CONNECT;
			default: return -1;
		}
	}

	public static String toString(int name) {
		switch (name) {
			case GET: return "GET";
			case POST: return "POST";
			case PUT: return "PUT";
			case HEAD: return "HEAD";
			case DELETE: return "DELETE";
			case OPTIONS: return "OPTIONS";
			case TRACE: return "TRACE";
			case CONNECT: return "CONNECT";
			default: return null;
		}
	}
}
