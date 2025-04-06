package roj.plugins.web.php;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.http.HttpUtil;
import roj.http.server.*;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.TypedKey;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/7/1 19:14
 */
public abstract class fcgiManager implements Router {
	static final Logger LOGGER = Logger.getLogger("FastCGI");
	static {LOGGER.setLevel(Level.WARN);}
	private static final TypedKey<fcgiContent> FCGI_Handler = new TypedKey<>("fcgi:handler");

	@Override
	public Content response(Request req, ResponseHeader rh) throws Exception {
		var h = req.connection().attachment(FCGI_Handler,null);
		if (h == null) {
			h = fcgi_pass(req, new MyHashMap<>());
			h.onSuccess(null);
		}

		if (h.isHeaderFinished()) return h;
		rh.enableAsyncResponse(60000);
		return null;
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		req.unshared();

		fcgiContent h;
		if (cfg != null) {
			h = fcgi_pass(req, new MyHashMap<>());
			cfg.postAccept(Integer.MAX_VALUE, 900000);
			cfg.postHandler(h);
		} else {
			h = null;
		}
		req.connection().attachment(FCGI_Handler,h);
	}

	protected fcgiContent fcgi_pass(Request req, Map<String, String> param) throws IllegalRequestException {
		param.putIfAbsent("SERVER_SOFTWARE", HttpServer11.SERVER_NAME+"(like apache)");
		param.putIfAbsent("GATEWAY_INTERFACE", "CGI/1.1");
		param.putIfAbsent("SERVER_NAME", "localhost");
		param.putIfAbsent("SERVER_PROTOCOL", "HTTP/1.1");
		if (req.isSecure()) param.put("HTTPS", "1");

		String field = req.header("content-length");
		if (!field.isEmpty()) param.putIfAbsent("CONTENT_LENGTH", field);

		field = req.header("content-type");
		if (!field.isEmpty()) param.putIfAbsent("CONTENT_TYPE", field);

		param.put("REQUEST_METHOD", HttpUtil.getMethodName(req.action()));
		param.put("REQUEST_URI", req.absolutePath());
		if (req.query() != null) param.put("QUERY_STRING", req.query());

		var addr = req.proxyRemoteAddress();
		param.put("REMOTE_ADDR", addr.getHostString());
		param.put("REMOTE_PORT", String.valueOf(addr.getPort()));
		addr = (InetSocketAddress) req.server().connection().localAddress();
		param.put("SERVER_ADDR", addr.getHostString());
		param.put("SERVER_PORT", String.valueOf(addr.getPort()));

		for (var entry : req.entrySet()) {
			param.put("HTTP_"+entry.getKey().toString().replace('-', '_').toUpperCase(Locale.ROOT), entry.getValue());
		}

		fcgi_set_param(req, param);
		var response = new fcgiContent(req);
		fcgi_attach(response, param);
		return response;
	}

	protected abstract void fcgi_set_param(Request req, Map<String, String> param) throws IllegalRequestException;
	protected abstract void fcgi_attach(fcgiContent response, Map<String, String> param);

	protected void connectionClosed(fcgiConnection conn) {}
	protected void requestFinished(fcgiConnection conn) {}
}