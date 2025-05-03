package roj.plugins;

import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.concurrent.PacketBuffer;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.http.WebSocketConnection;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.plugin.PluginDescriptor;
import roj.plugin.SimplePlugin;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2024/4/4 2:21
 */
@Deprecated
@SimplePlugin(id = "simpleMQ", desc = "基于websocket的实时消息队列", version = "1.1")
public class SimpleMQ extends Plugin {
	private Plugin easySso;

	@Override
	protected void onEnable() throws Exception {
		easySso = getPluginManager().getPluginInstance(PluginDescriptor.Role.PermissionManager);
		registerRoute("mq", new OKRouter().register(this), "PermissionManager");
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
				worker.close(WebSocketConnection.ERR_CLOSED, "插件卸载");
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
	final ConcurrentHashMap<String, DynByteBuf> dataCache = new ConcurrentHashMap<>();

	final class Worker extends WebSocketConnection {
		final PacketBuffer pb = new PacketBuffer(4);
		final PermissionHolder user;

		public Worker(PermissionHolder user) {this.user = user;}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			System.out.println("channelopend");
			send("computer info");
		}

		@Override
		public void handlerAdded(ChannelCtx ctx) {
			super.handlerAdded(ctx);
		}

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
				map = (ph == FRAME_TEXT ? ConfigMaster.JSON : ConfigMaster.MSGPACK).parse(in).asMap();
			} catch (IOException|ParseException e) {
				getLogger().warn("{}的消息解析失败", e, ch.remoteAddress());
				close(ERR_INVALID_DATA, "消息解析失败");
				return;
			}

			String eventId = map.getString("subscribe");
			if (!eventId.isEmpty()) {
				var event = subscribers.computeIfAbsent(eventId, Helpers.fnArrayList());

				boolean success;
				synchronized (event) { //noinspection AssignmentUsedAsCondition
					if (success = (!event.contains(this) && user.hasPermission("mq/subscribe/"+eventId))) {
						event.add(this);

						var buf = dataCache.get(eventId);
						if (buf != null) pb.offer(buf);
					}
				}

				send(success ? "true" : "false");
			}

			eventId = map.getString("unsubscribe");
			if (!eventId.isEmpty()) {
				var event = subscribers.getOrDefault(eventId, Collections.emptyList());

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
	public Content post(Request req, String event, @QueryParam(orDefault = "false") boolean cache) {
		event = event.replace('/', '.');

		var data = req.body();
		List<Worker> handlers = subscribers.getOrDefault(event, Collections.emptyList());

		var ph = getUser(req);
		if (ph == null || !ph.hasPermission("mq/post/"+event+(cache?"/cached":""))) {
			return Content.json("\"权限不足\"");
		}

		if (cache) {
			var prev = dataCache.put(event, data.copySlice());
			if (prev != null) ((ByteList) prev)._free();
		} else {
			dataCache.remove(event);
		}

		int count;
		if (!handlers.isEmpty()) synchronized (handlers) {
			count = handlers.size();
			for (int i = 0; i < count; i++) {
				handlers.get(i).asyncSend(event, data);
			}
		} else count = 0;

		return Content.json(String.valueOf(count));
	}

	@GET
	@Interceptor("cors")
	public Content query(Request req, String event) {
		event = event.replace('/', '.');

		var ph = getUser(req);
		if (ph == null || !ph.hasPermission("mq/query/"+event)) return Content.json("\"权限不足\"");

		var buffer = dataCache.get(event);
		return buffer == null ? req.server().code(204).noContent() : Content.bytes(buffer);
	}

	@Route("")
	@Interceptor("cors")
	public Object index(Request req) {
		if (!"websocket".equals(req.header("upgrade")))
			return req.server().code(503).cast("websocket server");

		return Content.websocket(req, request -> {
			var user = getUser(request);
			if (user == null) return null;

			var listener = new Worker(user);

			synchronized (workers) {
				if (disabled) return null;
				workers.add(listener);
			}

			return listener;
		});
	}

	@Interceptor
	public Object cors(Request req) {return req.checkOrigin(null);}

	@Nullable
	private PermissionHolder getUser(Request req) {return easySso.ipc(new TypedKey<>("getUser"), req);}
}