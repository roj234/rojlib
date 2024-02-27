package roj.plugins.mychat;

import roj.collect.IntSet;
import roj.concurrent.PacketBuffer;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.ws.WebSocketHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/7 18:48
 */
public abstract class WSChat extends WebSocketHandler {
	public static final int
		P_USER_STATE = 1, P_USER_INFO = 2,
		P_MESSAGE = 3, P_SYS_MESSAGE = 4,
		P_DATA_CHANGED = 5, P_ROOM_STATE = 6,
		P_GET_HISTORY = 8, P_REMOVE_HISTORY = 9, P_COLD_HISTORY = 14,
		P_NEW_FRIEND = 15;
	public static final int P_HEARTBEAT = 10, P_LOGOUT = 11, P_LOGIN = 12, P_ALERT = 13, P_JSON_PAYLOAD = 255;

	static final int USE_BINARY = 32, FIRST_HEART = 64;

	private JSONParser jl;
	private byte flag2;

	protected final IntSet known = new IntSet();
	protected final PacketBuffer pb = new PacketBuffer(32);
	protected boolean shutdownInProgress;

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		if (!pb.isEmpty()) {
			DynByteBuf b = IOUtil.getSharedByteBuf();
			b.ensureCapacity(256);

			while (true) {
				b.clear();
				if (pb.take(b) == null || !b.isReadable()) break;

				send((flag2 & USE_BINARY) != 0 ? FRAME_BINARY : FRAME_TEXT, b);
			}
		} else if (shutdownInProgress) error(ERR_OK, null);
	}

	public WSChat() {
		maxData = 262144;
		compressSize = 127;
	}

	@Override
	protected final void onData(int ph, DynByteBuf in) throws IOException {
		if (ph == FRAME_TEXT) {
			jsonPacket(in);
		} else {
			flag2 |= USE_BINARY;
			binaryPacket(in);
		}
	}

	private void binaryPacket(DynByteBuf in) throws IOException {
		switch (in.readByte() & 0xFF) {
			case P_LOGOUT:
				error(ERR_OK, null);
				break;
			case P_HEARTBEAT:
				in.rIndex--;
				send(FRAME_BINARY, in);
				if ((flag2 & FIRST_HEART) == 0) {
					init();
					flag2 |= FIRST_HEART;
				}
				break;
			case P_USER_INFO:
				while (in.isReadable()) requestUserInfo(in.readInt());
				break;
			case P_MESSAGE:
				message(in.readInt(), decodeToUTF(in));
				break;
			case P_REMOVE_HISTORY:
				requestClearHistory(in.readInt(), in.readInt());
				break;
			case P_GET_HISTORY:
				int id = in.readInt();
				int off = in.readInt();
				int len = in.readInt();
				requestHistory(id, decodeToUTF(in), off, len);
				break;
			case P_COLD_HISTORY:
				requestColdHistory(in.readInt());
				break;
			case P_NEW_FRIEND, P_JSON_PAYLOAD:
				error(ERR_INVALID_DATA, "未实现的函数");
				break;
			default:
				error(ERR_INVALID_DATA, "无效包类别 " + in.get(0));
				break;
		}
	}

	private void jsonPacket(DynByteBuf in) throws IOException {
		int mark = in.rIndex;
		try {
			if (jl == null) jl = (JSONParser) new JSONParser().charset(StandardCharsets.UTF_8);
			CMap map = jl.parse(in, JSONParser.NO_DUPLICATE_KEY).asMap();
			switch (map.getInteger("act")) {
				case P_LOGOUT:
					error(ERR_OK, null);
					break;
				case P_HEARTBEAT:
					in.rIndex = mark;
					send(FRAME_TEXT, in);
					if ((flag2 & FIRST_HEART) == 0) {
						init();
						flag2 |= FIRST_HEART;
					}
					break;
				case P_USER_INFO:
					CList id = map.getOrCreateList("id");
					for (int i = 0; i < id.size(); i++) {
						requestUserInfo(id.get(i).asInteger());
					}
					break;
				case P_MESSAGE:
					message(map.getInteger("to"), map.getString("msg"));
					break;
				case P_REMOVE_HISTORY:
					requestClearHistory(map.getInteger("id"), map.getInteger("time"));
					break;
				case P_GET_HISTORY:
					requestHistory(map.getInteger("id"), map.getString("filter"), map.getInteger("off"), map.getInteger("len"));
					break;
				case P_COLD_HISTORY:
					requestColdHistory(map.getInteger("id"));
					break;
				case P_NEW_FRIEND:
					error(ERR_INVALID_DATA, "未实现的函数");
					break;
				default:
					error(ERR_INVALID_DATA, "无效包类别 " + map.getInteger("act"));
			}
		} catch (Throwable e) {
			e.printStackTrace();
			error(ERR_INVALID_DATA, "JSON解析失败");
		}
	}

	protected void init() {}

	protected abstract void message(int to, CharSequence msg);

	protected abstract void requestUserInfo(int id);

	protected void requestHistory(int id, CharSequence filter, int off, int len) {}

	protected void requestClearHistory(int id, int timeout) {}

	protected void requestColdHistory(int id) {}

	public final void sendUserInfo(AbstractUser user) {
		known.add(user.id);
		user.addMoreInfo(known);

		ByteList b = IOUtil.getSharedByteBuf();
		user.put(b.put(P_USER_INFO));
		pb.offer(b);
	}

	public final void sendGroupChange(int groupId, AbstractUser user, boolean add) {
		if (add) known.add(user.id);

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(P_ROOM_STATE).putInt(groupId);
		if (add) {
			user.put(b);
		} else {
			b.putInt(user.id);
		}
		pb.offer(b);
	}

	public final void sendDataChanged(int userId) {
		if (!known.remove(userId)) return;

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(P_DATA_CHANGED).putInt(userId);
		pb.offer(b);
	}

	public final void sendOnlineState(int userId, byte online) {
		ByteList b = IOUtil.getSharedByteBuf();
		b.put(P_USER_STATE).putInt(userId).put(online);
		pb.offer(b);
	}

	public final void sendExternalLogout(String reason) {
		sendUTF(reason, P_LOGOUT);
		shutdownInProgress = true;
	}

	public final void sendAlert(String reason) {
		sendUTF(reason, P_ALERT);
	}

	private void sendUTF(String utf, int id) {
		ByteList b = IOUtil.getSharedByteBuf();
		b.put(id).putUTFData(utf);
		pb.offer(b);
	}

	public final void sendMessage(AbstractUser user, Message message, boolean sys) {
		if (message.text.length() > maxData - 10) throw new IllegalArgumentException("Message too long");
		int userId = user.id;
		known.add(userId);

		ByteList b = IOUtil.getSharedByteBuf();
		b.put((byte) (sys ? P_SYS_MESSAGE : P_MESSAGE))
		 .putInt(userId).putInt(message.uid).putLong(message.time)
		 .putUTFData(message.text);
		pb.offer(b);
	}

	public final void sendHistory(int userId, int total, List<Message> msgs) {
		ByteList b = IOUtil.getSharedByteBuf();
		b.put(P_GET_HISTORY).putInt(userId).putInt(total).putInt(msgs.size());
		for (int i = 0; i < msgs.size(); i++) {
			Message msg = msgs.get(i);
			b.putLong(msg.time).putInt(msg.uid).putUTF(msg.text);
		}
		pb.offer(b);
	}
}