package roj.webui;

import roj.ci.annotation.Public;
import roj.concurrent.ExecutorService;
import roj.concurrent.Promise;
import roj.concurrent.TaskPool;
import roj.concurrent.TaskThread;
import roj.config.ConfigMaster;
import roj.config.mapper.ObjectMapper;
import roj.http.WebSocket;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.TextContent;
import roj.http.server.auto.GET;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.reflect.Telescope;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.function.ExceptionalConsumer;
import roj.util.function.ExceptionalRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2026/01/19 23:43
 */
public class RPCInstance {
	private ExecutorService executor;
	private final Logger logger;

	private final Map<Class<?>, ExceptionalConsumer<?, IOException>> callbacks;
	private Set<Class<?>> sendables;
	private final ObjectMapper objectMapper;
	private final ExceptionalRunnable<IOException> windowClosed, windowOpened;

	private WebSocket webSocket;
	private Promise<Integer> closePromise;
	private boolean useBinaryMessage;

	private static final VarHandle WEB_SOCKET = Telescope.lookup().findVarHandle(RPCInstance.class, "webSocket", WebSocket.class);

	public Logger getLogger() {return logger;}

	static class Init {
		int version;
		boolean isReconnect;
	}

	RPCInstance(String name, Class<?> caller, Map<Class<?>, ExceptionalConsumer<?, IOException>> callbacks,
					   ExceptionalRunnable<IOException> windowClosed, ExceptionalRunnable<IOException> windowOpened,
					   boolean parallel) {
		this.logger = Logger.getLogger(name);
		this.callbacks = callbacks;
		this.objectMapper = ObjectMapper.getInstance(ObjectMapper.CHECK_INTERFACE|ObjectMapper.CHECK_PARENT|ObjectMapper.PARSE_DYNAMIC, caller.getClassLoader());
		this.windowOpened = windowOpened;
		for (var entry : callbacks.entrySet()) {
			objectMapper.registerType(entry.getKey());
		}
		objectMapper.registerType(Init.class);
		this.windowClosed = windowClosed;
		if (parallel) executor = TaskPool.common();
		else {
			TaskThread executor1 = new TaskThread();
			executor = executor1;
			executor1.start();
		}
	}

	@GET("wsrpc.js")
	@Public
	private Content js(Request req) throws IOException {
		req.responseHeader().put("cache-control", "immutable");
		return new TextContent(IOUtil.readString(new File("D:\\work\\MCMake\\RojLib-HTML\\tinyrpc\\wsrpc.js")), "text/javascript");
	}

	@GET
	@Public
	private Content wsrpc(Request req) {
		if (req.containsKey("upgrade", "websocket")) {
			return Content.websocket(req, request -> {
				WebSocket newInstance = new WebSocket() {
					{ this.compressThreshold = 256; }

					@Override
					protected void onText(DynByteBuf data, boolean isLast) throws IOException {
						useBinaryMessage = false;

						Object value;
						try {
							value = objectMapper.read(data, Object.class, ConfigMaster.JSON);
							RPC(value);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					@Override
					protected void onBinary(DynByteBuf data, boolean isLast) throws IOException {
						useBinaryMessage = true;

						Object value;
						try {
							value = objectMapper.read(data, Object.class, ConfigMaster.MSGPACK);
							RPC(value);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					private void RPC(Object value) throws IOException {
						Class<?> type = value.getClass();
						var consumer = callbacks.get(type);
						if (consumer == null) throw new CorruptedInputException("Type "+type+" not allowed to read");
						executor.executeUnsafe(() -> consumer.accept(Helpers.cast(value)));
					}

					@Override
					public void channelOpened(ChannelCtx ctx) throws IOException {
						super.channelOpened(ctx);
						windowOpened.run();
						logger.info("RPC客户端已连接");
					}

					@Override
					public void channelClosed(ChannelCtx ctx) throws IOException {
						super.channelClosed(ctx);
						WEB_SOCKET.compareAndSet(RPCInstance.this, this, null);
						if (windowClosed != null) windowClosed.run();
						logger.info("RPC客户端已断开({}), 消息: {}", errCode, errMsg);

						if (errCode == 1000) System.exit(0);

						var promise = closePromise;
						if (promise != null) ((Promise.Result) promise).resolve(errCode);
					}
				};

				var oldInstance = (WebSocket)WEB_SOCKET.getAndSet(this, newInstance);
				if (oldInstance != null) {
					Lock lock = oldInstance.ch.channel().lock();
					lock.lock();
					try {
						oldInstance.sendClose(WebSocket.ERR_CLOSED, "another location");
					} catch (IOException e) {
						Helpers.athrow(e);
					} finally {
						lock.unlock();
					}
				}

				return newInstance;
			});
		} else {
			return Content.text("200 OK");
		}
	}

	public Promise<Integer> close() {
		WebSocket ws = webSocket;
		if (ws == null) return Promise.resolve(1000);

		Lock lock = ws.ch.channel().lock();
		lock.lock();
		try {
			closePromise = Promise.manual();
			ws.sendClose(1000, "close");
			return closePromise;
		} catch (Exception e) {
			return Promise.reject(e);
		} finally {
			lock.unlock();
		}
	}

	public void sendData(Object message) throws IOException {
		WebSocket ws = webSocket;
		if (ws != null) {
			ByteList buf = new ByteList();
			boolean useBinaryMessage1 = useBinaryMessage;
			ConfigMaster configType = useBinaryMessage1 ? ConfigMaster.MSGPACK : ConfigMaster.JSON;
			objectMapper.writer(Object.class).write(configType, Helpers.cast(message), buf);

			Lock lock = ws.ch.channel().lock();
			lock.lock();
			try {
				ws.send(useBinaryMessage1 ? WebSocket.FRAME_BINARY : WebSocket.FRAME_TEXT, buf);
			} finally {
				lock.unlock();
				buf.release();
			}
		}
	}
}
