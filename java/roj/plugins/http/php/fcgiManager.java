package roj.plugins.http.php;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.*;
import roj.text.logging.Level;
import roj.text.logging.Logger;

import java.io.IOException;
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

	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		var h = (fcgiResponse) req.localCtx().remove("fcgi:handler");
		if (h == null) {
			h = fcgi_pass(req, new MyHashMap<>());
			h.onSuccess();
		}

		if (h.isHeaderFinished()) return h;
		req.server().asyncResponse();
		return null;
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		if (cfg != null) {
			cfg.postAccept(Integer.MAX_VALUE, 900000);

			fcgiResponse h;
			try {
				h = fcgi_pass(req, new MyHashMap<>());
			} catch (IllegalRequestException e) {
				throw e;
			}  catch (IOException e) {
				throw new IllegalRequestException(502, e);
			}

			cfg.postHandler(h);
			req.localCtx().put("fcgi:handler", h);
		} else {
			req.localCtx().remove("fcgi:handler");
		}
	}

	protected fcgiResponse fcgi_pass(Request req, Map<String, String> param) throws IOException {
		param.putIfAbsent("SERVER_SOFTWARE", HttpServer11.SERVER_NAME);
		param.putIfAbsent("GATEWAY_INTERFACE", "CGI/1.1");
		param.putIfAbsent("SERVER_NAME", "localhost");
		param.putIfAbsent("SERVER_PROTOCOL", "HTTP/1.1");

		String field = req.getField("content-length");
		if (!field.isEmpty()) {
			Integer.parseInt(field);
			param.putIfAbsent("CONTENT_LENGTH", field);
		}

		field = req.getField("content-type");
		if (!field.isEmpty()) param.putIfAbsent("CONTENT_TYPE", field);

		param.put("REQUEST_METHOD", HttpUtil.getMethodName(req.action()));
		param.put("REQUEST_URI", req.path());
		if (req.query() != null) param.put("QUERY_STRING", req.query());

		var addr = (InetSocketAddress) req.server().ch().remoteAddress();
		param.put("REMOTE_ADDR", addr.getHostString());
		param.put("REMOTE_PORT", String.valueOf(addr.getPort()));
		addr = (InetSocketAddress) req.server().ch().localAddress();
		param.put("SERVER_ADDR", addr.getHostString());
		param.put("SERVER_PORT", String.valueOf(addr.getPort()));

		for (Map.Entry<CharSequence, String> entry : req.entrySet()) {
			param.put("HTTP_"+entry.getKey().toString().replace('-', '_').toUpperCase(Locale.ROOT), entry.getValue());
		}

		fcgi_set_param(req, param);

		var server = req.server();
		server.ch().readInactive();
		var response = new fcgiResponse(server);

		TaskPool.Common().pushTask(() -> {
			try {
				fcgi_attach(response, param);
			} catch (Throwable ex) {
				response.fail(ex);
			} finally {
				try {
					if (server.ch() != null) server.ch().readActive();
				} catch (Throwable ignored) {}
			}

		});
		return response;
	}

	protected abstract void fcgi_set_param(Request req, Map<String, String> param) throws IOException;
	protected abstract void fcgi_attach(fcgiResponse response, Map<String, String> param) throws IOException;

	protected void connectionClosed(fcgiConnection conn) {}
	protected void requestFinished(fcgiConnection conn) {}
	protected void requestAborted(fcgiConnection conn) {}
}