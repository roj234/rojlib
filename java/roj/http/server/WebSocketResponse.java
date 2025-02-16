package roj.http.server;

import roj.http.HttpUtil;
import roj.http.ws.WebSocketHandler;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/2/14 18:26
 */
final class WebSocketResponse implements HFinishHandler {
	static final Set<String> EMPTY_PROTOCOL = Collections.singleton("");
	static Response websocket(Request req, Function<Request, WebSocketHandler> newHandler, Set<String> protocols) {
		var rh = req.server();

		String ver = req.getField("sec-websocket-version");
		String protocol = req.getField("sec-websocket-protocol");
		if (TextUtil.isNumber(ver) != 0 || Integer.parseInt(ver) > 13 || !protocols.contains(protocol)) {
			rh.code(HttpUtil.UNAVAILABLE);
			return Response.text("Unsupported protocol \""+protocol+"\"");
		}

		String key = req.getField("sec-websocket-key");
		rh.code(HttpUtil.SWITCHING_PROTOCOL).headers().putAllS("""
			upgrade: websocket\r
			connection: Upgrade\r
			sec-websocket-version: 13""");

		var uc = IOUtil.SharedCoder.get();
		ByteList in = uc.byteBuf; in.clear();
		var sha1 = HttpCache.getInstance().sha1();

		//magic number而已
		sha1.update(in.putAscii(key).putAscii("258EAFA5-E914-47DA-95CA-C5AB0DC85B11").list, 0, in.wIndex());

		rh.header("Sec-WebSocket-Accept", uc.encodeBase64(sha1.digest()));
		if (!protocol.isEmpty()) rh.header("sec-websocket-protocol", protocol);

		boolean zip;
		String ext = req.getField("sec-websocket-extensions");
		//noinspection AssignmentUsedAsCondition
		if (zip = ext.contains("permessage-deflate")) {
			rh.header("sec-websocket-extensions", "permessage-deflate");
			// "Per-Message Compressed": RSV1 => compressed bit
		}

		rh.onFinish(new WebSocketResponse(newHandler, req, zip));
		return null;
	}

	private final Function<Request, WebSocketHandler> newHandler;
	private final Request req;
	private final boolean zip;
	private WebSocketResponse(Function<Request, WebSocketHandler> handler, Request req, boolean zip) {
		newHandler = handler;
		this.req = req;
		this.zip = zip;
	}

	@Override
	public boolean onRequestFinish(HttpServer11 tcp) {
		WebSocketHandler h;
		if (tcp.hasError() || (h = newHandler.apply(req)) == null) return false;
		if (zip) h.enableZip();
		tcp.ch().addLast("WS-Handler", h);
		return true;
	}
}