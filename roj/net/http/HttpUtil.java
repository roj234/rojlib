package roj.net.http;

import roj.io.IOUtil;
import roj.net.http.srv.HttpServer11;
import roj.net.http.srv.Request;

import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/24 0024 2:01
 */
public class HttpUtil {
	public static Request request() {
		return (Request) HttpServer11.TSO.get().ctx.get("CURRENT_REQUEST");
	}

	public static String htmlspecial(CharSequence str) {
		return IOUtil.getSharedCharBuf().append(str).replaceMulti(array("&", "<", ">", "\"", "'"), array("&amp;", "&lt;", "&gt;", "&quot;", "&#039;")).toString();
	}

	public static String htmlspecial_decode(CharSequence str) {
		return IOUtil.getSharedCharBuf().append(str).replaceMulti(array("&lt;", "&gt;", "&quot;", "&#039;", "&amp;"), array("<", ">", "\"", "'", "&")).toString();
	}

	private static String[] array(String... s) {
		return s;
	}

	static final Pattern pa = Pattern.compile("(up.browser|up.link|mmp|symbian|smartphone|midp|wap|phone|iphone|ipad|ipod|android|xoom)", Pattern.CASE_INSENSITIVE);
	public static boolean is_wap(Request req) {
		String ua = req.get("user-agent");
		if (ua != null && pa.matcher(ua).matches()) return true;

		return req.header("accept").contains("application/vnd.wap.xhtml+xml");
	}

	// 禁止缓存
	public static void no_cache() {
		//header("Pragma:no-cache\r\n");
		//header("Cache-Control:no-cache\r\n");
		//header("Expires:0\r\n");
	}
}