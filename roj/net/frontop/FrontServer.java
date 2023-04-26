package roj.net.frontop;

import roj.collect.MyHashMap;
import roj.concurrent.PacketBuffer;
import roj.config.JSONParser;
import roj.config.data.CMapping;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.SelectorLoop;
import roj.net.http.srv.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * todo Vueize
 * @author Roj234
 * @since 2022/11/10 0010 16:05
 */
public abstract class FrontServer extends WebsocketManager implements Router {
	private static final MyHashMap<String, String> tmp = new MyHashMap<>();
	public static String res(String name) throws IOException {
		String v = tmp.get(name);
		if (v == null) {
			synchronized (tmp) {
				tmp.put(name, v = IOUtil.readResUTF("META-INF/html/" + name));
			}
		}
		return v;
	}

	private final String HTML;
	protected AtomicReference<WebsocketHandler> w = new AtomicReference<>();
	protected PacketBuffer pb = new PacketBuffer(10);
	public FrontServer(String html) {
		HTML = html;
	}

	public void simpleRun(InetSocketAddress addr) throws IOException {
		this.loop = HttpServer11.simple(addr, 64, this).daemon(true).launch();
	}

	public void simpleRun(InetSocketAddress addr, SelectorLoop loop) throws IOException {
		this.loop = HttpServer11.simple(addr, 64, this).loop(loop).launch();
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		switch (req.path()) {
			case "/bundle.min.css":
				return new StringResponse(res("bundle.min.css"), "text/css");
			case "/bundle.min.js":
				return new StringResponse(res("bundle.min.js"), "text/javascript");
			case "/":
				if ("websocket".equals(req.getField("Upgrade"))) {
					return switchToWebsocket(req, rh);
				}
				return new StringResponse(HTML, "text/html");
		}
		return rh.code(403).returnNull();
	}

	protected abstract void onWorkerData(CMapping data) throws IOException;
	protected abstract void onWorkerTick() throws IOException;
	protected abstract void onWorkerJoin() throws IOException;

	protected void send(CharSequence val) {
		pb.offer(IOUtil.SharedCoder.get().encodeR(val));
	}

	protected void update(String id, boolean val) throws IOException {
		update(id, val?"开启":"关闭");
	}
	protected void update(String id, CharSequence val) throws IOException {
		CharList sb = IOUtil.getSharedCharBuf();
		sb.append("{\"_\":\"update\",\"id\":\"");
		ITokenizer.addSlashes(sb, id).append("\",\"value\":\"");
		ITokenizer.addSlashes(sb, val).append("\"}");

		pb.offer(IOUtil.SharedCoder.get().encodeR(sb));
	}

	protected void append(String id, CharSequence val) throws IOException {
		CharList sb = IOUtil.getSharedCharBuf();
		sb.append("{\"_\":\"append\",\"id\":\"");
		ITokenizer.addSlashes(sb, id).append("\",\"value\":\"");
		ITokenizer.addSlashes(sb, val).append("\"}");

		pb.offer(IOUtil.SharedCoder.get().encodeR(sb));
	}

	@Override
	protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
		return new WebsocketHandler() {
			final JSONParser parser = new JSONParser();

			@Override
			protected void onData(int ph, DynByteBuf in) throws IOException {
				if (ph != FRAME_TEXT) {
					error(ERR_INVALID_DATA, "unexpected binary frame");
					return;
				}

				CMapping map;
				try {
					map = parser.parseRaw(in, JSONParser.LITERAL_KEY | JSONParser.NO_DUPLICATE_KEY).asMap();
				} catch (Exception e) {
					error(ERR_INVALID_DATA, "json parse failed");
					return;
				}
				onWorkerData(map);
			}

			@Override
			public void handlerAdded(ChannelCtx ctx) {
				try {
					WebsocketHandler w1 = w.getAndSet(this);
					if (w1 != null) w1.error(1000, "Logged in another location");
					onWorkerJoin();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void channelClosed(ChannelCtx ctx) throws IOException {
				w.compareAndSet(this, null);
			}

			@Override
			public void channelTick(ChannelCtx ctx) throws IOException {
				super.channelTick(ctx);

				DynByteBuf buf = ctx.allocate(false, 2048);
				try {
					while (!pb.isEmpty()) {
						send(FRAME_TEXT, pb.take(buf));
						buf.clear();
					}
				} finally {
					ctx.reserve(buf);
				}

				onWorkerTick();
			}
		};
	}
}
