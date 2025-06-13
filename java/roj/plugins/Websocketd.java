package roj.plugins;

import org.jetbrains.annotations.Nullable;
import roj.collect.HashMap;
import roj.collect.ArrayList;
import roj.config.data.Type;
import roj.http.WebSocket;
import roj.http.server.*;
import roj.http.server.auto.OKRouter;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.plugin.Plugin;
import roj.text.FastCharset;
import roj.text.logging.Logger;
import roj.ui.Terminal;
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
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author solo6975
 * @since 2022/3/20 22:53
 */
public class Websocketd extends Plugin implements Router {
	private static final HashMap<String, List<String>> cmdList = new HashMap<>();

	static volatile List<Worker> workers = new ArrayList<>();

	private static Logger logger;
	private OKRouter.Dispatcher manager;

	protected void onEnable() {
		manager = getInterceptor("PermissionManager");
		logger = getLogger();
		for (var entry : getConfig().getMap("path_to_command").entrySet()) {
			var value = entry.getValue();
			var cmd = value.getType() == Type.STRING ? Collections.singletonList(value.asString()) : value.asList().toStringList();
			registerRoute(entry.getKey(), this);
			cmdList.put(entry.getKey(), cmd);

			getLogger().info("URL子路径: {}, 指令: {}", entry.getKey(), cmd);
		}
	}

	@Override
	protected void onDisable() {
		List<Worker> prev;
		synchronized (cmdList) {
			prev = workers;
			workers = Collections.emptyList();
		}

		for (int i = 0; i < prev.size(); i++) {
			var worker = prev.get(i);
			var ch = worker.ch.channel();
			var lock = ch.lock();
			if (lock.tryLock()) {
				try {
					worker.sendClose(WebSocket.ERR_CLOSED, "插件卸载");
				} catch (Throwable e) {
					logger.error(e);
				}
			}
			IOUtil.closeSilently(ch);
		}
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		if (manager != null) manager.invoke(req, req.server(), cfg);
	}
	@Override
	public Content response(Request req, ResponseHeader rh) throws IOException {
		var cmd = cmdList.get(req.absolutePath());
		if (cmd != null) {
			if ("websocket".equals(req.header("upgrade"))) return Content.websocket(req, new Worker(cmd));
			var archive = getDescription().getArchive();
			return ZipRouter.zip(req, archive, archive.getEntry("index.html"));
		}
		return rh.code(404).noContent();
	}

	static final class Worker extends WebSocket implements Function<Request, WebSocket> {
		private Process process;

		private CharsetEncoder sysEnc;
		private CharsetDecoder sysDec;

		private CharBuffer tmp1;
		private ByteBuffer tmp2, sndRem;

		private final List<String> _cmd;
		Worker(List<String> cmd) {this._cmd = cmd;}

		@Override
		public WebSocket apply(Request request) {
			if (workers == Collections.EMPTY_LIST) return null;
			try {
				process = new ProcessBuilder().command(_cmd).redirectErrorStream(true)
					.redirectInput(ProcessBuilder.Redirect.PIPE)
					.redirectOutput(ProcessBuilder.Redirect.PIPE).start();

				tmp2 = ByteBuffer.allocate(1024);

				if (Terminal.nativeCharset != StandardCharsets.UTF_8) {
					sysEnc = Terminal.nativeCharset.newEncoder();
					sysDec = Terminal.nativeCharset.newDecoder();

					tmp1 = CharBuffer.allocate(1024);
					sndRem = ByteBuffer.allocate(8);
					sndRem.flip();
				}
				synchronized (cmdList) {workers.add(this);}
				logger.info("会话 "+Integer.toHexString(hashCode())+" 开始");
			} catch (Exception e) {
				if (process != null) process.destroy();
				logger.error("启动进程失败", e);
				return null;
			}
			return this;
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);

			process.destroy();
			synchronized (cmdList) {workers.remove(this);}
			logger.info("会话 "+Integer.toHexString(hashCode())+" 结束: "+errCode+"@"+errMsg);
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);

			if (!process.isAlive()) {
				sendClose(ERR_OK, "进程终止");
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
								sendClose(ERR_UNEXPECTED, "charset decode error for " + Terminal.nativeCharset);
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
				sendClose(ERR_OK, "进程终止");
			}
		}

		@Override
		protected void onData(int frameType, DynByteBuf in) throws IOException {
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
						in.readFully(array, 0, len);
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
					FastCharset.UTF8().decodeFixedIn(in, Math.min(in.readableBytes(), tmp1.capacity()), tmp1);
					tmp1.flip();

					sndBuf.clear();

					CoderResult r = sysEnc.encode(tmp1, sndBuf, false);
					if (r.isError() || r.isMalformed() || r.isUnmappable()) {
						sendClose(ERR_UNEXPECTED, "charset encode error for " + Terminal.nativeCharset);
						return;
					}

					out.write(sndBuf.array(), 0, sndBuf.position());
				}
			}
			out.flush();
		}
	}
}