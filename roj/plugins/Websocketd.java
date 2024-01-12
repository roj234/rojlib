package roj.plugins;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.srv.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.platform.Plugin;
import roj.text.TextUtil;
import roj.text.UTF8MB4;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/3/20 22:53
 */
public class Websocketd extends WebsocketManager implements Router {
	List<String> cmd;
	static volatile boolean disabled;
	static final List<CmdWorker> workers = new ArrayList<>();

	public static class PluginHandler extends Plugin {
		protected void onEnable() {
			for (Map.Entry<String, CEntry> entry : getConfig().getMap("path_to_command").entrySet()) {
				Websocketd ws = new Websocketd();
				CEntry value = entry.getValue();
				ws.cmd = value.getType() == Type.STRING ? Collections.singletonList(value.asString()) : value.asList().asStringList();
				registerRoute(entry.getKey(), ws);

				getLogger().info("Path: {}, Command: {}", entry.getKey(), ws.cmd);
			}
		}

		@Override
		protected void onDisable() {
			for (String path : getConfig().getMap("path_to_command").keySet())
				unregisterRoute(path);

			SimpleList<CmdWorker> copy;
			synchronized (workers) {
				disabled = true;
				copy = new SimpleList<>(workers);
				workers.clear();
			}

			for (int i = 0; i < copy.size(); i++) {
				CmdWorker worker = copy.get(i);
				try {
					worker.error(WebsocketHandler.ERR_CLOSED, "plugin disabled");
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
		try {
			if (!disabled) return new CmdWorker(cmd);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static final MyHashMap<String, String> tmp = new MyHashMap<>();
	private static String res(String name) throws IOException {
		String v = tmp.get(name);
		if (v == null) tmp.put(name, v = IOUtil.getTextResource("META-INF/html/"+name));
		return v;
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		switch (req.path()) {
			case "bundle.min.css": return new StringResponse(res("bundle.min.css"), "text/css");
			case "bundle.min.js": return new StringResponse(res("bundle.min.js"), "text/javascript");
			case "":
				if ("websocket".equals(req.getField("Upgrade"))) return switchToWebsocket(req, rh);
				return new StringResponse(res("websocketd_ui.html"), "text/html");
			default: return rh.code(404).returnNull();
		}
	}

	static final class CmdWorker extends WebsocketHandler {
		Process process;

		CharsetEncoder sysEnc;
		CharsetDecoder sysDec;

		CharBuffer tmp1;
		ByteBuffer tmp2, sndRem;

		CmdWorker(List<String> cmd) throws IOException {
			process = new ProcessBuilder().command(cmd).redirectErrorStream(true)
				.redirectInput(ProcessBuilder.Redirect.PIPE)
				.redirectOutput(ProcessBuilder.Redirect.PIPE).start();

			tmp2 = ByteBuffer.allocate(1024);

			if (TextUtil.DefaultOutputCharset != StandardCharsets.UTF_8) {
				sysEnc = TextUtil.DefaultOutputCharset.newEncoder();
				sysDec = TextUtil.DefaultOutputCharset.newDecoder();

				tmp1 = CharBuffer.allocate(1024);
				sndRem = ByteBuffer.allocate(8);
				sndRem.flip();
			}
			synchronized (workers) { workers.add(this); }
			System.out.println("会话 "+Integer.toHexString(hashCode())+" 开始");
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);

			process.destroy();
			synchronized (workers) { workers.remove(this); }
			System.out.println("会话 "+Integer.toHexString(hashCode())+" 结束: "+errCode+"@"+errMsg);
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);

			if (!process.isAlive()) {
				error(ERR_OK, "进程终止");
				return;
			}

			InputStream in = process.getInputStream();

			if (in.available() > 0) {
				CharBuffer tmpChar = this.tmp1;
				ByteBuffer inByte = this.tmp2;
				ByteList outByte = IOUtil.getSharedByteBuf();

				inByte.clear();
				if (sndRem != null) inByte.put(sndRem);

				do {
					int len = Math.min(in.available(), inByte.remaining());
					len = in.read(inByte.array(), inByte.position(), len);
					if (len <= 0) break;

					inByte.limit(inByte.position() + len).position(0);

					if (sysDec != null) {
						while (true) {
							tmpChar.clear();

							CoderResult r = sysDec.decode(inByte, tmpChar, false);
							if (r.isError() || r.isMalformed() || r.isUnmappable()) {
								error(ERR_UNEXPECTED, "charset decode error for " + TextUtil.DefaultOutputCharset);
							}

							if (tmpChar.position() == 0) {
								inByte.compact();
								break;
							}
							tmpChar.flip();

							send(FRAME_TEXT, outByte.putUTFData(tmpChar));
							outByte.clear();

							if (!inByte.hasRemaining()) {
								inByte.clear();
								break;
							}
						}
					} else {
						send(FRAME_TEXT, ByteList.wrap(inByte.array(), 0, len));
					}
				} while (in.available() > 0);

				if (sndRem != null) {
					sndRem.clear();
					inByte.flip();
					sndRem.put(inByte).flip();
				}
			}

			if (in.available() < 0) {
				error(ERR_OK, "进程终止");
			}
		}

		@Override
		protected void onData(int ph, DynByteBuf in) throws IOException {
			OutputStream out = process.getOutputStream();
			if (in.hasArray() && sysDec == null) {
				// UTF-8 Array Stream
				out.write(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
				in.rIndex = in.wIndex();
			} else if (sysDec == null) {
				// UTF-8 Direct Memory
				byte[] array = ArrayCache.getByteArray(1024, false);
				try {
					while (in.isReadable()) {
						int len = Math.min(in.readableBytes(), array.length);
						in.read(array, 0, len);
						out.write(array, 0, len);
					}
				} finally {
					ArrayCache.putArray(array);
				}
			} else {
				// Conversation

				CharBuffer tmp1 = this.tmp1;
				ByteBuffer sndBuf = this.tmp2;

				while (in.isReadable()) {
					tmp1.clear();
					UTF8MB4.CODER.decodeFixedIn(in, Math.min(in.readableBytes(), tmp1.capacity()), tmp1);
					tmp1.flip();

					sndBuf.clear();

					CoderResult r = sysEnc.encode(tmp1, sndBuf, false);
					if (r.isError() || r.isMalformed() || r.isUnmappable()) {
						error(ERR_UNEXPECTED, "charset encode error for " + TextUtil.DefaultOutputCharset);
						return;
					}

					out.write(sndBuf.array(), 0, sndBuf.position());
				}
			}
			out.flush();
		}
	}
}