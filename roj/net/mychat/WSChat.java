package roj.net.mychat;

import roj.collect.IntSet;
import roj.concurrent.PacketBuffer;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.serial.ToJson;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.ws.WebsocketHandler;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/7 18:48
 */
public abstract class WSChat extends WebsocketHandler {
	public static final int
		P_USER_STATE = 1, P_USER_INFO = 2,
		P_MESSAGE = 3, P_SYS_MESSAGE = 4,
		P_DATA_CHANGED = 5, P_ROOM_STATE = 6,
		P_SPACE_NEW = 7,
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
				if (pb.take(b) == null) break;

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
		switch (in.get() & 0xFF) {
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
			case P_NEW_FRIEND:
				addFriendResult(in.readInt(), in.get());
				break;
			case P_JSON_PAYLOAD:
				jsonPacket(in);
				break;
			default:
				error(ERR_INVALID_DATA, "无效包类别 " + in.get(0));
				break;
		}
	}

	private void jsonPacket(DynByteBuf in) throws IOException {
		int mark = in.rIndex;
		try {
			CharList s = decodeToUTF(in);
			if (jl == null) jl = new JSONParser();
			CMapping map = jl.parse(s, JSONParser.LITERAL_KEY | JSONParser.NO_DUPLICATE_KEY).asMap();
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
					addFriendResult(map.getInteger("id"), map.getInteger("result"));
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
	//@Override
	//protected void onClosed() {}

	protected abstract void message(int to, CharSequence msg);

	protected abstract void requestUserInfo(int id);

	protected void addFriendResult(int id, int result) {}

	protected void requestHistory(int id, CharSequence filter, int off, int len) {}

	protected void requestClearHistory(int id, int timeout) {}

	protected void requestColdHistory(int id) {}

	public final void sendUserInfo(AbstractUser user) {
		known.add(user.id);
		user.addMoreInfo(known);

		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			user.put(b.put((byte) P_USER_INFO));
		} else {
			ToJson ser = new ToJson();
			ser.valueMap();

			ser.key("act");
			ser.value(P_USER_INFO);

			user.put(ser);
			b.putUTFData(ser.getValue());
		}
		pb.offer(b);
	}

	public final void sendGroupChange(int groupId, AbstractUser user, boolean add) {
		if (add) known.add(user.id);

		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_ROOM_STATE).putInt(groupId);
			if (add) {
				user.put(b);
			} else {
				b.putInt(user.id);
			}
		} else {
			ToJson ser = new ToJson();
			ser.valueMap();

			ser.key("act");
			ser.value(P_ROOM_STATE);

			ser.key("id");
			ser.value(groupId);

			if (add) {
				ser.key("user");
				ser.valueMap();
				user.put(ser);
				ser.pop();
			} else {
				ser.key("uid");
				ser.value(user.id);
			}
			b.putUTFData(ser.getValue());
		}
		pb.offer(b);
	}

	public final void sendDataChanged(int userId) {
		if (!known.remove(userId)) return;

		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_DATA_CHANGED).putInt(userId);
		} else {
			CMapping map = new CMapping();
			map.put("act", P_DATA_CHANGED);
			map.put("id", userId);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendOnlineState(int userId, byte online) {
		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_USER_STATE).putInt(userId).put(online);
		} else {
			CMapping map = new CMapping();
			map.put("act", P_USER_STATE);
			map.put("id", userId);
			map.put("on", online);
			b.putUTFData(map.toShortJSONb());
		}
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
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) id).putUTFData(utf);
		} else {
			CMapping map = new CMapping();
			map.put("act", id);
			map.put("desc", utf);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendMessage(AbstractUser user, Message message, boolean sys) {
		if (message.text.length() > maxData - 10) throw new IllegalArgumentException("Message too long");
		int userId = user.id;
		known.add(userId);

		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) (sys ? P_SYS_MESSAGE : P_MESSAGE))
			 .putInt(userId).putInt(message.uid).putLong(message.time)
			 .putUTFData(message.text);
		} else {
			CMapping map = new CMapping();
			map.put("act", sys ? P_SYS_MESSAGE : P_MESSAGE);
			map.put("id", userId);
			if (!sys) {
				if (userId != message.uid) {
					map.put("uid", message.uid);
				}
			} else {
				map.put("style", message.uid);
			}
			CMapping subMap = map.getOrCreateMap("msg");
			subMap.put("time", message.time);
			subMap.put("text", message.text);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendNotLogin() {
		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_LOGIN);
		} else {
			CMapping map = new CMapping();
			map.put("act", P_LOGIN);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendSpaceChanged() {
		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_SPACE_NEW);
		} else {
			CMapping map = new CMapping();
			map.put("act", P_SPACE_NEW);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendHistory(int userId, int total, List<Message> msgs) {
		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_GET_HISTORY).putInt(userId).putInt(total).putInt(msgs.size());
			for (int i = 0; i < msgs.size(); i++) {
				Message msg = msgs.get(i);
				b.putLong(msg.time).putInt(msg.uid).putUTF(msg.text);
			}
		} else {
			CMapping map = new CMapping();
			map.put("act", P_GET_HISTORY);
			map.put("id", userId);
			map.put("total", total);
			CList data = new CList(msgs.size());
			for (int i = 0; i < msgs.size(); i++) {
				Message msg = msgs.get(i);
				CMapping subMap = new CMapping(2);
				data.add(subMap);
				subMap.put("time", msg.time);
				subMap.put("text", msg.text);
				subMap.put("uid", msg.uid);
			}
			map.put("data", data);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}

	public final void sendNewFriend(int userId, String text) {
		ByteList b = IOUtil.getSharedByteBuf();
		if ((flag2 & USE_BINARY) != 0) {
			b.put((byte) P_NEW_FRIEND).putUTFData(text).putInt(userId);
		} else {
			CMapping map = new CMapping();
			map.put("act", P_NEW_FRIEND);
			map.put("id", userId);
			map.put("desc", text);
			b.putUTFData(map.toShortJSONb());
		}
		pb.offer(b);
	}
}