package roj.http;

import roj.collect.CharMap;
import roj.crypt.Base64;
import roj.http.server.Request;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.util.regex.Pattern;

/**
 * A helper class which define the HTTP request methods, response codes and some CORS-specified utility methods
 * @author Roj234
 * @since 2020/11/28 20:17
 */
public class HttpUtil {
	// region Action/Method
	public static final int GET = 0, POST = 1, PUT = 2, HEAD = 3, DELETE = 4, OPTIONS = 5, TRACE = 6, CONNECT = 7;
	public static byte getMethodId(String method) {
		if (method == null) return -1;
		return switch (method) {
			case "GET" -> GET;
			case "POST" -> POST;
			case "PUT" -> PUT;
			case "HEAD" -> HEAD;
			case "DELETE" -> DELETE;
			case "OPTIONS" -> OPTIONS;
			case "TRACE" -> TRACE;
			case "CONNECT" -> CONNECT;
			default -> -1;
		};
	}
	public static String getMethodName(int id) {
		return switch (id) {
			case GET -> "GET";
			case POST -> "POST";
			case PUT -> "PUT";
			case HEAD -> "HEAD";
			case DELETE -> "DELETE";
			case OPTIONS -> "OPTIONS";
			case TRACE -> "TRACE";
			case CONNECT -> "CONNECT";
			default -> null;
		};
	}
	// endregion
	// region Response Code
	public static final int
		SWITCHING_PROTOCOL = 101,
		OK = 200,
		MOVED_PERMANENTLY = 301, FOUND = 302, NOT_MODIFIED = 304,
		BAD_REQUEST = 400, FORBIDDEN = 403, NOT_FOUND = 404, METHOD_NOT_ALLOWED = 405, TIMEOUT = 408, ENTITY_TOO_LARGE = 413, URI_TOO_LONG = 414, UPGRADE_REQUIRED = 426,
		INTERNAL_ERROR = 500, UNAVAILABLE = 503;

	public static String getCodeDescription(int code) {return codes.getOrDefault((char) code, "Internal Server Error");}

	private static final CharMap<String> codes = new CharMap<>();
	static {
		codes.put((char) 100, "Continue");
		codes.put((char) 101, "Switching Protocols");
		codes.put((char) 200, "OK");
		codes.put((char) 201, "Created");
		codes.put((char) 202, "Accepted");
		codes.put((char) 203, "Non-Authoritative Information");
		codes.put((char) 204, "No Content");
		codes.put((char) 205, "Reset Content");
		codes.put((char) 206, "Partial Content");
		codes.put((char) 300, "Multiple Choices");
		codes.put((char) 301, "Moved Permanently");
		codes.put((char) 302, "Found");
		codes.put((char) 303, "See Other");
		codes.put((char) 304, "Not Modified");
		codes.put((char) 305, "Use Proxy");
		codes.put((char) 306, "(Unused)");
		codes.put((char) 307, "Temporary Redirect");
		codes.put((char) 400, "Bad Request");
		codes.put((char) 401, "Unauthorized");
		codes.put((char) 402, "Payment Required");
		codes.put((char) 403, "Forbidden");
		codes.put((char) 404, "Not Found");
		codes.put((char) 405, "Method Not Allowed");
		codes.put((char) 406, "Not Acceptable");
		codes.put((char) 407, "Proxy Authentication Required");
		codes.put((char) 408, "Request Timeout");
		codes.put((char) 409, "Conflict");
		codes.put((char) 410, "Gone");
		codes.put((char) 411, "Length Required");
		codes.put((char) 412, "Precondition Failed");
		codes.put((char) 413, "Request Entity Too Large");
		codes.put((char) 414, "Request-URI Too Long");
		codes.put((char) 415, "Unsupported Media Type");
		codes.put((char) 416, "Requested Range Not Satisfiable");
		codes.put((char) 417, "Expectation Failed");
		codes.put((char) 431, "Request Header Fields Too Large");
		codes.put((char) 426, "Upgrade Required");
		codes.put((char) 500, "Internal Server Error");
		codes.put((char) 501, "Not Implemented");
		codes.put((char) 502, "Bad Gateway");
		codes.put((char) 503, "Service Unavailable");
		codes.put((char) 504, "Gateway Timeout");
		codes.put((char) 505, "HTTP Version Not Supported");
	}
	// endregion
	//region cache-control
	// Vary用于请求头决定的缓存
	public static final String
		NO_CACHE = "no-store",
		CACHED = "max-age=604800",
		CACHED_REVALIDATE = "no-cache",
		IMMUTABLE = "max-age=604800, immutable";
	//endregion
	//region Auth
	public static String makeBasicAuth(String user, String pass) {
		ByteList in = new ByteList().putUTFData(user).put(':').putUTFData(pass);
		String auth = Base64.encode(in, IOUtil.getSharedCharBuf()).toString();
		in._free();
		return auth;
	}
	//endregion

	private static final Pattern MOBILE = Pattern.compile("(mobile|wap|phone|ios|android)", Pattern.CASE_INSENSITIVE);
	public static boolean isMobile(Request req) {return "?1".equals(req.header("sec-ch-ua-mobile")) || MOBILE.matcher(req.header("user-agent")).matches();}
}