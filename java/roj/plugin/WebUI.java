package roj.plugin;

import roj.concurrent.TaskPool;
import roj.config.serial.ToJson;
import roj.crypt.Base64;
import roj.crypt.VoidCrypt;
import roj.http.server.AsyncContent;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.ResponseHeader;
import roj.http.server.auto.Body;
import roj.http.server.auto.GET;
import roj.http.server.auto.Interceptor;
import roj.http.server.auto.POST;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.text.DateTime;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import static roj.plugin.PanTweaker.CONFIG;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/23 1:40
 */
@Interceptor("PermissionManager")
final class WebUI {
	private final boolean terminal;
	public WebUI(boolean terminal) {this.terminal = terminal;}

	@GET
	public Content list(Request req) {
		var ser = new ToJson();
		ser.valueMap();
		ser.key("ok");
		ser.value(true);
		ser.key("data");
		ser.value(Panger.motds.get(((int)System.nanoTime()&Integer.MAX_VALUE)%Panger.motds.size()));
		ser.key("menus");
		CONFIG.getList("webui").accept(ser);
		return Content.json(ser.getValue());
	}

	@GET
	public void terminal(Request req, ResponseHeader rh) throws IOException {
		if (!terminal || System.currentTimeMillis() < XTerm.timeout) {
			rh.code(403).body(Content.internalError("该功能未启用"));
			return;
		}

		if (req.header("upgrade").equals("websocket")) {
			rh.body(Content.websocket(req, request -> new XTerm()));
		} else {
			rh.body(Content.text("200 OK"));
		}
	}
	private static final class XTerm extends WebTerminal {
		static final ThreadLocal<WebTerminal> ACTIVE = new ThreadLocal<>();

		static long timeout;
		static {
			Panger.CMD.register(literal("disconnect").executes(ctx -> {
				var wt = ACTIVE.get();
				if (wt == null) Terminal.warning("该指令只能在Web终端中执行");
				// 因为在当前线程上，所以有锁
				else wt.sendClose(ERR_CLOSED, "goodbye");
			}));
			Panger.CMD.onVirtualKey(key -> {
				if (key == (Terminal.VK_CTRL| KeyEvent.VK_Q)) {
					timeout = System.currentTimeMillis() + 300000;
					Terminal.error("Web终端功能关闭至"+ DateTime.toLocalTimeString(timeout));
					return false;
				}
				if (key == (Terminal.VK_CTRL|KeyEvent.VK_C)) {
					if (ACTIVE.get() != null) {
						Terminal.warning("为了防止误触发, Ctrl+C已在Web终端中禁用, 请使用stop指令");
						return false;
					}
				}
				return null;
			});
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);
			if (System.currentTimeMillis() < timeout) sendClose(ERR_CLOSED, "disabled");
		}

		@Override
		protected void onData(int frameType, DynByteBuf in) {
			ACTIVE.set(this);
			try {
				super.onData(frameType, in);
			} finally {
				ACTIVE.remove();
			}
		}
	}

	static final class DEncrypt {
		List<String> keys, texts, types, paddings;
		String algorithm;
	}
	@POST
	public Object denCrypt(Request req, @Body DEncrypt json) {
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
				case "hex" -> text = IOUtil.decodeHex(str);
				case "base64" -> text = IOUtil.decodeBase64(str).toByteArray();
				default -> {return "参数错误";}
			}

			pairs[i] = new VoidCrypt.CipherPair(keys.get(i).getBytes(StandardCharsets.UTF_8), new ByteList(text));
			length += pairs[i].key.length + text.length;
		}
		if (length > 524288) return "内容过多(512KB max)";

		if (!json.algorithm.equals("r")) req.server().enableCompression();

		var callback = new AsyncContent();
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
		try {
			DynByteBuf plaintext = VoidCrypt.statistic_decrypt(key.getBytes(StandardCharsets.UTF_8), IOUtil.decodeBase64(text), new ByteList());
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
					return (Content) rh -> {
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