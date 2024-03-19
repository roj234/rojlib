package roj.net.http.ws;

import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.ResponseHeader;
import roj.net.http.server.StringResponse;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * Websocket协议 <br>
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC6455</a>
 *
 * @author Roj234
 * @since 2021/2/14 18:26
 */
public abstract class WebSocketServer {
	private final Set<String> validProtocol;
	private final MessageDigest SHA1;

	public WebSocketServer() {
		this.validProtocol = new MyHashSet<>(4);
		this.validProtocol.add("");
		try {
			this.SHA1 = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException();
		}
	}

	public final Set<String> getValidProtocol() {return validProtocol;}

	private void calcKey(String key, ResponseHeader out) {
		String SECRET = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

		IOUtil uc = IOUtil.SharedCoder.get();
		ByteList in = uc.byteBuf; in.clear();
		SHA1.update(in.putAscii(key).putAscii(SECRET).list, 0, in.wIndex());

		out.header("Sec-WebSocket-Accept", uc.encodeBase64(SHA1.digest()));
	}

	public final Response switchToWebsocket(Request req) {
		ResponseHeader rh = req.server();

		String ver = req.getField("sec-websocket-version");
		String protocol = req.getField("sec-websocket-protocol");
		if (TextUtil.isNumber(ver) != 0 || Integer.parseInt(ver) > 13 || !validProtocol.contains(protocol)) {
			rh.code(503);
			return new StringResponse("Unsupported protocol \""+protocol+"\"");
		}

		String key = req.getField("sec-websocket-key");
		rh.code(101).headers("upgrade: websocket\r\n" +
			"connection: Upgrade\r\n" +
			"sec-websocket-version: 13");
		calcKey(key, rh);
		if (!protocol.isEmpty()) {
			rh.header("sec-websocket-protocol", protocol);
		}

		boolean zip = false;
		String ext = req.getField("sec-websocket-extensions");
		if (ext.contains("permessage-deflate")) {
			zip = true;
			rh.header("sec-websocket-extensions", "permessage-deflate");
			//The "Per-Message Compressed" bit, which indicates whether or not
			//the message is compressed.  RSV1 is set for compressed messages
			//and unset for uncompressed messages.
		}

		WebSocketHandler w = newWorker(req, rh);


		if (zip) w.enableZip();

		rh.onFinish(rh1 -> {
			if (rh1.hasError()) return false;
			rh1.ch().addLast("WS-Handler", w);
			return true;
		});
		return null;
	}

	protected abstract WebSocketHandler newWorker(Request req, ResponseHeader handle);
}