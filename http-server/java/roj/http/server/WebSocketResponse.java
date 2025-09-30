package roj.http.server;

import roj.crypt.CryptoFactory;
import roj.http.HttpUtil;
import roj.http.WebSocket;
import roj.io.IOUtil;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/2/14 18:26
 */
final class WebSocketResponse implements ResponseFinishHandler {
	static final Set<String> EMPTY_PROTOCOL = Collections.singleton("");
	static Content websocket(Request req, Function<Request, WebSocket> newHandler, Set<String> protocols) {
		var rh = req.response();

		String ver = req.header("sec-websocket-version");
		String protocol = req.header("sec-websocket-protocol");
		if (TextUtil.isNumber(ver) != 0 || Integer.parseInt(ver) > 13 || !protocols.contains(protocol)) {
			rh.code(HttpUtil.UNAVAILABLE);
			return Content.text("Unsupported protocol \""+protocol+"\"");
		}

		String key = req.header("sec-websocket-key");
		rh.code(HttpUtil.SWITCHING_PROTOCOL).headers().putAllS("""
			upgrade: websocket\r
			connection: Upgrade\r
			sec-websocket-version: 13""");

		var buf = IOUtil.getSharedByteBuf();
		var sha1 = CryptoFactory.getSharedDigest("SHA-1");

		//magic number而已
		sha1.update(buf.putAscii(key).putAscii("258EAFA5-E914-47DA-95CA-C5AB0DC85B11").list, 0, buf.wIndex());

		rh.setHeader("Sec-WebSocket-Accept", IOUtil.encodeBase64(sha1.digest()));
		if (!protocol.isEmpty()) rh.setHeader("sec-websocket-protocol", protocol);

		boolean zip;
		String ext = req.header("sec-websocket-extensions");
		//noinspection AssignmentUsedAsCondition
		if (zip = ext.contains("permessage-deflate")) {
			rh.setHeader("sec-websocket-extensions", "permessage-deflate");
			// "Per-Message Compressed": RSV1 => compressed bit
		}

		rh.onFinish(new WebSocketResponse(newHandler, req, zip));
		return null;
	}

	private final Function<Request, WebSocket> newHandler;
	private final Request req;
	private final boolean zip;
	private WebSocketResponse(Function<Request, WebSocket> handler, Request req, boolean zip) {
		newHandler = handler;
		this.req = req;
		this.zip = zip;
	}

	@Override
	public boolean onResponseFinish(Response response, boolean success) {
		WebSocket h;
		if (!success || (h = newHandler.apply(req)) == null) return false;
		if (zip) h.enableZip();
		response.connection().addLast("WS-Handler", h);
		return true;
	}
}