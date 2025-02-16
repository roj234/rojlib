package roj.plugin;

import roj.concurrent.TaskPool;
import roj.config.serial.ToJson;
import roj.crypt.Base64;
import roj.crypt.VoidCrypt;
import roj.http.server.AsyncResponse;
import roj.http.server.Request;
import roj.http.server.Response;
import roj.http.server.ResponseHeader;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import static roj.plugin.PanTweaker.CONFIG;

/**
 * @author Roj234
 * @since 2024/7/23 0023 1:40
 */
@Interceptor("PermissionManager")
final class WebUI {
	static final class Menu {
		String name, url, className;
		List<Menu> child;
	}

	private final boolean terminal;
	public WebUI(boolean terminal) {this.terminal = terminal;}

	@GET
	public Response list(Request req) {
		var ser = new ToJson();
		ser.valueMap();
		ser.key("ok");
		ser.value(true);
		ser.key("data");
		ser.value(Panger.motds.get(((int)System.nanoTime()&Integer.MAX_VALUE)%Panger.motds.size()));
		ser.key("menus");
		CONFIG.getList("webui").accept(ser);
		return Response.json(ser.getValue());
	}

	@GET
	public void terminal(Request req, ResponseHeader rh) throws IOException {
		if (!terminal || System.currentTimeMillis() < WebTerminal.timeout) {
			rh.code(403).body(Response.internalError("该功能未启用"));
			return;
		}

		if (req.getField("upgrade").equals("websocket")) {
			rh.body(Response.websocket(req, request -> new WebTerminal()));
		} else {
			rh.body(Response.text("200 OK"));
		}
	}

	public static final class EncryptRequest {
		List<String> keys, texts, types, paddings;
		String algorithm;
	}
	@POST
	@Body(From.JSON)
	public Object denCrypt(Request req, EncryptRequest json) {
		req.responseHeader().put("content-type", "text/plain");

		if (json.keys.isEmpty() || json.keys.size() != json.texts.size()) return "参数错误";

		int length = 0;
		var pairs = new VoidCrypt.CipherPair[json.keys.size()];
		List<String> keys = json.keys;
		for (int i = 0; i < keys.size(); i++) {
			byte[] text;
			var str = json.texts.get(i);
			switch (json.types.get(i)) {
				case "UTF8" -> text = IOUtil.getSharedByteBuf().putUTFData(str).toByteArray();
				case "GB18030" -> text = IOUtil.getSharedByteBuf().putGBData(str).toByteArray();
				case "UTF-16LE" -> text = str.getBytes(StandardCharsets.UTF_16LE);
				case "hex" -> text = IOUtil.SharedCoder.get().decodeHex(str);
				case "base64" -> text = IOUtil.SharedCoder.get().decodeBase64(str).toByteArray();
				default -> {return "参数错误";}
			}

			pairs[i] = new VoidCrypt.CipherPair(keys.get(i).getBytes(StandardCharsets.UTF_8), new ByteList(text));
			length += pairs[i].key.length + text.length;
		}
		if (length > 524288) return "内容过多(512KB max)";

		if (!json.algorithm.equals("r")) req.server().enableCompression();

		var callback = new AsyncResponse();
		TaskPool.Common().submit(() -> {
			ByteList buf = IOUtil.getSharedByteBuf();
			try {
				switch (json.algorithm) {
					case "r" -> VoidCrypt._encrypt2r(new SecureRandom(), buf, pairs);
					case "i" -> VoidCrypt._encrypt1i(new SecureRandom(), buf, pairs);
					case "b" -> VoidCrypt._encrypt1b(new SecureRandom(), buf, pairs);
				}
				callback.offerAndRelease(Base64.encode(buf, new ByteList()));
			} catch (Exception e){
				buf.clear();
				callback.offer(buf.putUTFData("加密失败: "+e));
			} finally {
				callback.setEof();
			}
		});
		return callback;
	}

	@POST
	public Object denDecrypt(Request req, String key, String text, String type, String padding) {
		var sc = IOUtil.SharedCoder.get();
		try {
			DynByteBuf plaintext = VoidCrypt.statistic_decrypt(key.getBytes(StandardCharsets.UTF_8), sc.decodeBase64(text), new ByteList());
			switch (padding) {
				case "zero":
					int i = plaintext.wIndex();
					if (i > 0) {
						while (plaintext.get(i - 1) == 0) i--;
						plaintext.wIndex(i);
					}
					break;
			}

			switch (type) {
				case "UTF8","GB18030","UTF-16LE":
					req.responseHeader().put("content-type", "text/plain; charset="+type);
					req.responseHeader().put("content-length", String.valueOf(plaintext.readableBytes()));
					return (Response) rh -> {
						rh.write(plaintext);
						return plaintext.isReadable();
					};
				case "hex": return plaintext.hex();
				case "base64": return plaintext.base64();
			}
		} catch (Exception e) {
			return "解密失败: "+e;
		}
		return "参数错误";
	}
}