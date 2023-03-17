package roj.net.frontop;

import roj.collect.MyHashMap;
import roj.config.JSONParser;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.net.http.srv.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2022/11/10 0010 16:05
 */
public class InteractiveCLI extends WebsocketManager implements Router {
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

	WebsocketHandler active;
	String request_response;
	int state;

	ReentrantLock lock = new ReentrantLock();
	Condition income = lock.newCondition();

	public InteractiveCLI(String html) {
		HTML = html;
	}

	public void runOn(InetSocketAddress addr) throws IOException {
		this.loop = HttpServer11.simple(addr, 64, this).daemon(true).launch();
	}

	public String call(String msg) {
		lock.lock();
		try {
			state = 1;
			request_response = msg;
			income.awaitUninterruptibly();
			msg = request_response;
			request_response = null;
			return msg;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		switch (req.path()) {
			case "bundle.min.css": return new StringResponse(res("bundle.min.css"), "text/css");
			case "bundle.min.js": return new StringResponse(res("bundle.min.js"), "text/javascript");
			case "":
				if ("websocket".equals(req.getField("Upgrade")))
					return switchToWebsocket(req, rh);
				req.responseHeader().put("content-type", "text/html");
				return FileResponse.response(req, new DiskFileInfo(new File(HTML)) {
					public void prepare(ResponseHeader srv, Headers h) {}
				});
		}
		return rh.code(403).returnNull();
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

				String response = in.readUTF(in.readableBytes()).trim();

				lock.lock();
				try {
					if (state == 2) {
						request_response = response;
						state = 3;
						income.signal();

						send("\n===========\n已提交: "+response+"\n===========\n");
					} else {
						send("\n===========\n无效的状态: "+state+"\n"+request_response+"\n===========\n");
					}
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void handlerAdded(ChannelCtx ctx) {
				lock.lock();
				try {
					if (active != null) active.error(1000, "Logged in another location");
					active = this;
					if (state == 2) state = 1;
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void channelClosed(ChannelCtx ctx) {
				lock.lock();
				if (active == this) active = null;
				lock.unlock();
			}

			@Override
			public void channelTick(ChannelCtx ctx) throws IOException {
				super.channelTick(ctx);

				lock.lock();
				try {
					if (state == 1) {
						send(request_response);
						state = 2;
					}
				} finally {
					lock.unlock();
				}
			}
		};
	}
}
