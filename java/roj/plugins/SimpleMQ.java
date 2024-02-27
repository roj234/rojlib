package roj.plugins;

import roj.collect.SimpleList;
import roj.concurrent.PacketBuffer;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.HttpUtil;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.auto.*;
import roj.net.http.ws.WebSocketHandler;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2024/4/4 0004 2:21
 */
@SimplePlugin(id = "simpleMQ", desc = "基于websocket的实时消息队列", version = "1.1")
public class SimpleMQ extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		registerRoute("mq", new OKRouter().register(this));
	}

	@Override
	protected void onDisable() {
		SimpleList<Worker> copy;
		synchronized (workers) {
			disabled = true;
			copy = new SimpleList<>(workers);
			workers.clear();
		}

		for (int i = 0; i < copy.size(); i++) {
			Worker worker = copy.get(i);
			Lock lock = worker.ch.channel().lock();
			lock.lock();
			try {
				worker.error(WebSocketHandler.ERR_CLOSED, "插件卸载");
				worker.ch.close();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				lock.unlock();
			}
		}
	}

	boolean disabled;
	final SimpleList<Worker> workers = new SimpleList<>();
	final ConcurrentHashMap<String, List<Worker>> subscribers = new ConcurrentHashMap<>();

	final class Worker extends WebSocketHandler {
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
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);
			synchronized (workers) {workers.remove(this);}
		}

		@Override
		protected void onData(int ph, DynByteBuf in) throws IOException {
			CMap map;
			try {
				map = ph == FRAME_TEXT ? ConfigMaster.JSON.parse(in).asMap() : ConfigMaster.NBT.parse(in).asMap();
			} catch (IOException|ParseException e) {
				getLogger().warn("{}的消息解析失败", e, ch.remoteAddress());
				error(ERR_INVALID_DATA, "消息解析失败");
				return;
			}

			String val = map.getString("subscribe");
			if (!val.isEmpty()) {
				var event = subscribers.computeIfAbsent(val, Helpers.fnArrayList());

				boolean success;
				synchronized (event) { //noinspection AssignmentUsedAsCondition
					if (success = !event.contains(this)) event.add(this);
				}

				send(success ? "true" : "false");
			}

			val = map.getString("unsubscribe");
			if (!val.isEmpty()) {
				var event = subscribers.getOrDefault(val, Collections.emptyList());

				boolean success;
				if (event.isEmpty()) success = false;
				else synchronized (event) {success = event.remove(this);}

				send(success ? "true" : "false");
			}
		}

		public void asyncSend(String event, DynByteBuf data) {
			ByteList tmp = IOUtil.getSharedByteBuf();
			tmp.putAscii("{\"event\":\"").putAscii(event).putAscii("\",\"data\":").put(data).putAscii("}");
			pb.offer(tmp);
		}
	}

	@POST
	@Interceptor("cors")
	@Body(From.GET)
	public Response post(Request req, String event) {
		ByteList data = req.postBuffer();
		List<Worker> handlers = subscribers.getOrDefault(event, Collections.emptyList());

		int count;
		if (!handlers.isEmpty()) synchronized (handlers) {
			count = handlers.size();
			for (int i = 0; i < count; i++) {
				handlers.get(i).asyncSend(event, data);
			}
		} else count = 0;

		return Response.json(String.valueOf(count));
	}

	@Route
	@Interceptor("cors")
	public Object subscribe(Request req) {
		if (!"websocket".equals(req.getField("upgrade")))
			return req.server().code(503).returns("websocket server");
		return Response.websocket(req, request -> {
			var listener = new Worker();

			synchronized (workers) {
				if (disabled) return null;
				workers.add(listener);
			}

			return listener;
		});
	}

	@Interceptor
	public Object cors(Request req) {
		if (HttpUtil.isCORSPreflight(req)) {
			req.server().code(204).header(
				"Access-Control-Allow-Headers: "+req.getField("access-control-request-headers")+"\r\n" +
					"Access-Control-Allow-Origin: "+req.getField("origin")+"\r\n" +
					"Access-Control-Max-Age: 2592000\r\n" +
					"Access-Control-Allow-Methods: *");
			return Response.EMPTY;
		}
		req.server().header("Access-Control-Allow-Origin", "*");
		return null;
	}
}