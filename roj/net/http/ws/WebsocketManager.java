package roj.net.http.ws;

import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.net.ch.SelectorLoop;
import roj.net.http.srv.*;
import roj.text.UTFCoder;
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
public abstract class WebsocketManager {
	private final Set<String> validProtocol;
	private final MessageDigest SHA1;

	public SelectorLoop loop;

	public WebsocketManager() {
		this.validProtocol = new MyHashSet<>(4);
		this.validProtocol.add("");
		try {
			this.SHA1 = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException();
		}
	}

	public final Set<String> getValidProtocol() {
		return validProtocol;
	}

	// B-F control frame

	private void calcKey(String key, ResponseHeader out) {
		String SECRET = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList in = uc.byteBuf; in.clear();
		SHA1.update(in.putAscii(key).putAscii(SECRET).list, 0, in.wIndex());

		out.header("Sec-WebSocket-Accept", uc.encodeBase64(uc.wrap(SHA1.digest())));
	}

	public final Response switchToWebsocket(Request req, ResponseHeader handle) {
		String ver = req.getField("sec-websocket-version");
		String protocol = req.getField("sec-websocket-protocol");
		if (!ver.equals("13") || !validProtocol.contains(protocol)) {
			handle.code(503);
			return new StringResponse("Unsupported protocol \"" + protocol + "\"");
		}

		String key = req.getField("sec-websocket-key");
		handle.code(101).headers("upgrade: websocket\r\n" +
			"connection: Upgrade\r\n" +
			"sec-websocket-version: 13");
		calcKey(key, handle);
		if (!protocol.isEmpty()) {
			handle.header("sec-websocket-protocol", protocol);
		}

		boolean zip = false;
		String ext = req.getField("sec-websocket-extensions");
		if (ext.contains("permessage-deflate")) {
			zip = true;
			handle.header("sec-websocket-extensions", "permessage-deflate");
			//The "Per-Message Compressed" bit, which indicates whether or not
			//the message is compressed.  RSV1 is set for compressed messages
			//and unset for uncompressed messages.
		}

		WebsocketHandler w = newWorker(req, handle);

		w.ch = handle.ch();
		if (zip) w.enableZip();

		handle.finishHandler(registerLater(w));
		return null;
	}

	protected abstract WebsocketHandler newWorker(Request req, ResponseHeader handle);

	protected HFinishHandler registerLater(WebsocketHandler w) {
		return (rh) -> {
			if (rh.hasError()) return false;
			w.ch.channel().addLast("WS-Handler", w);
			return true;
		};
	}

}
