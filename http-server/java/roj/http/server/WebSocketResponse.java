package roj.http.server;

import roj.crypt.CryptoFactory;
import roj.http.Headers;
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
		rh.code(HttpUtil.SWITCHING_PROTOCOL).headers().parseFromText("""
			upgrade: websocket\r
			connection: Upgrade\r
			sec-websocket-version: 13""");

		var buf = IOUtil.getSharedByteBuf();
		var sha1 = CryptoFactory.getSharedDigest("SHA-1");

		//magic number而已
		sha1.update(buf.putAscii(key).putAscii("258EAFA5-E914-47DA-95CA-C5AB0DC85B11").list, 0, buf.wIndex());

		rh.setHeader("sec-webSocket-accept", IOUtil.encodeBase64(sha1.digest()));
		if (!protocol.isEmpty()) rh.setHeader("sec-websocket-protocol", protocol);

		int compression = 0;
		var clientExt = req.getElement("sec-websocket-extensions");
		if (clientExt.containsKey("permessage-deflate")) {
			var serverExt = new Headers.HeaderElement();
			serverExt.add("permessage-deflate");

			compression |= WebSocket.COMPRESS_AVAILABLE;
			if (clientExt.containsKey("client_no_context_takeover")) {
				serverExt.add("client_no_context_takeover");
				compression |= WebSocket.REMOTE_NO_CTX;
			}

			// JVM的Deflater设置这个不太行
			/*if (serverExt.contains("client_max_window_bits")) {

			}*/

			rh.setHeader("sec-websocket-extensions", serverExt.toString());
		}

		rh.onFinish(new WebSocketResponse(newHandler, req, compression));
		return null;
	}

	private final Function<Request, WebSocket> newHandler;
	private final Request req;
	private final byte compression;
	private WebSocketResponse(Function<Request, WebSocket> handler, Request req, int compression) {
		newHandler = handler;
		this.req = req;
		this.compression = (byte) compression;
	}

	@Override
	public boolean onResponseFinish(Response response, boolean success) {
		WebSocket ws;
		if (!success || (ws = newHandler.apply(req)) == null) return false;
		ws.setCompression(compression);
		response.connection().addLast("WS-Handler", ws);
		return true;
	}
}