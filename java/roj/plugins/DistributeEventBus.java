package roj.plugins;

import roj.concurrent.PacketBuffer;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.HttpUtil;
import roj.net.http.server.*;
import roj.net.http.server.auto.*;
import roj.net.http.ws.WebSocketHandler;
import roj.net.http.ws.WebSocketServer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since 2024/4/4 0004 2:21
 */
public class DistributeEventBus extends WebSocketServer {
	public static void main(String[] args) throws IOException {
		DistributeEventBus bus = new DistributeEventBus();
		InetSocketAddress addr = new InetSocketAddress(4399);
		HttpServer11.simple(addr, 1024, new OKRouter().register(bus)).launch();
		System.out.println(addr);
		HighResolutionTimer.activate();
	}

	public DistributeEventBus() {}

	ConcurrentHashMap<String, List<AsyncEventListener>> subscribers = new ConcurrentHashMap<>();

	@Override
	protected WebSocketHandler newWorker(Request req, ResponseHeader handle) {return new AsyncEventListener();}

	final class AsyncEventListener extends WebSocketHandler {
		PacketBuffer pb = new PacketBuffer(15);

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);
			if (!pb.isEmpty()) {
				ByteList buf = IOUtil.getSharedByteBuf();
				while (pb.mayTake(buf)) {
					send(FRAME_TEXT, buf);
					buf.clear();
				}

				assert pb.isEmpty();
			}
		}

		@Override
		protected void onData(int ph, DynByteBuf in) throws IOException {
			if (ph != FRAME_TEXT) {
				send("frame_text_required");
				return;
			}

			CMap map;
			try {
				map = ConfigMaster.JSON.parse(in).asMap();
			} catch (ParseException e) {
				send(e.getMessage());
				return;
			}

			switch (map.getString("action")) {
				case "add" -> {
					List<AsyncEventListener> event = subscribers.computeIfAbsent(map.getString("event"), Helpers.fnArrayList());
					boolean success;

					synchronized (event) { //noinspection AssignmentUsedAsCondition
						if (success = !event.contains(this)) event.add(this);
					}

					send(success ? "true" : "false");
				}
				case "del" -> {
					List<AsyncEventListener> event = subscribers.getOrDefault(map.getString("event"), Collections.emptyList());
					boolean success;

					if (event.isEmpty()) success = false;
					else synchronized (event) {success = event.remove(this);}

					send(success ? "true" : "false");
				}
			}
		}

		public void asyncSend(String event, DynByteBuf data) {
			ByteList tmp = IOUtil.getSharedByteBuf();
			tmp.putAscii("{\"event\":\"").putAscii(event).putAscii("\",\"data\":").put(data).putAscii("}");
			pb.offer(tmp);
		}
	}

	@Route
	@Accepts(Accepts.POST)
	@Interceptor("cors")
	@Body(From.GET)
	public Response post(Request req, String event) {
		ByteList data = req.postBuffer();
		List<AsyncEventListener> handlers = subscribers.getOrDefault(event, Collections.emptyList());

		int count;
		if (!handlers.isEmpty()) synchronized (handlers) {
			count = handlers.size();
			for (int i = 0; i < count; i++) {
				handlers.get(i).asyncSend(event, data);
			}
		} else count = 0;

		return new StringResponse(String.valueOf(count), "application/json");
	}

	@Route
	@Interceptor("cors")
	public Object subscribe(Request req) {
		if ("websocket".equals(req.getField("Upgrade"))) return switchToWebsocket(req);
		return req.server().code(400).returns("use ws:// to connect");
	}

	@Interceptor
	public Object cors(Request req) {
		if (HttpUtil.isCORSPreflight(req)) {
			req.responseHeader().putAllS(
				"Access-Control-Allow-Headers: "+req.getField("access-control-request-headers")+"\r\n" +
					"Access-Control-Allow-Origin: " + req.getField("Origin") + "\r\n" +
					"Access-Control-Max-Age: 2592000\r\n" +
					"Access-Control-Allow-Methods: *");
			return Response.EMPTY;
		}
		req.responseHeader().put("Access-Control-Allow-Origin", "*");
		return null;
	}
}