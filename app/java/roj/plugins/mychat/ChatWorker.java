package roj.plugins.mychat;

import roj.collect.ArrayList;
import roj.collect.IntSet;
import roj.collect.RingBuffer;
import roj.concurrent.PacketBuffer;
import roj.http.WebSocket;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/7 18:48
 */
class ChatWorker extends WebSocket {
	public static final int
		P_USER_STATE = 1, P_USER_INFO = 2,
		P_MESSAGE = 3, P_SYS_MESSAGE = 4,
		P_DATA_CHANGED = 5, P_ROOM_STATE = 6,
		P_GET_HISTORY = 8, P_REMOVE_HISTORY = 9,
		P_HEARTBEAT = 10, P_LOGOUT = 11, P_LOGIN = 12, P_ALERT = 13,
		P_COLD_HISTORY = 14,
		P_NEW_FRIEND = 15;

	@Deprecated static final int FIRST_HEART = 64;

	private byte flag2;

	private final ChatUser user;
	private final ChatManager server;
	public ChatWorker(ChatManager server, ChatUser user) {
		maxData = 262144;
		compressSize = 127;
		this.server = server;
		this.user = user;
	}

	@Override
	protected final void onData(int frameType, DynByteBuf in) throws IOException {
		switch (in.readUnsignedByte()) {
			case P_LOGOUT -> sendClose(ERR_OK, null);
			case P_HEARTBEAT -> {
				in.rIndex--;
				send(FRAME_BINARY, in);
				if ((flag2 & FIRST_HEART) == 0) {
					onOpen();
					flag2 |= FIRST_HEART;
				}
			}
			case P_USER_INFO -> {
				while (in.isReadable()) {
					var u = server.getSubject(in.readInt());
					if (u == null) {
						sendClose(ERR_INVALID_DATA, "无效的数据包[UserId]");
						break;
					}
					sendUserInfo(u);
				}
			}
			case P_MESSAGE -> {
				var u = server.getSubject(in.readInt());
				if (u == null) {
					sendClose(ERR_INVALID_DATA, "无效的数据包[UserId]");
				} else {
					u.sendMessage(server, new Message(user.id, decodeToUTF(in).toString()), false);
				}
			}
			case P_REMOVE_HISTORY -> deleteHistory(in.readInt(), in.readInt());
			case P_GET_HISTORY -> {
				int id = in.readInt();
				int off = in.readInt();
				int len = in.readInt();
				getHistory(id, decodeToUTF(in), off, len);
			}
			case P_COLD_HISTORY -> unloadHistory(in.readInt());
			default -> sendClose(ERR_INVALID_DATA, "未实现的函数 "+in.get(0));
		}
	}

	protected void onOpen() {
		ChatSubject g = ChatManager.testGroup;
		sendMessage(g, new Message(Message.STYLE_SUCCESS, "欢迎使用MyChat2!"), true);
		sendMessage(g, new Message(Message.STYLE_WARNING | Message.STYLE_BAR, "2022年做的时尚小垃圾"), true);
		sendMessage(g, new Message(Message.STYLE_ERROR, "快打开F12看史山吧！"), true);
		sendMessage(g, new Message(0, """
					前端工程化在这种几万行代码的大型项目上确实有可取之处
					而不再是什么娱乐行为

					欢迎使用MyChat2 (下称"软件")
					本软件由Roj234独立开发并依法享有其知识产权

					软件以MIT协议开源,并"按原样提供", 不包含任何显式或隐式的担保,
					上述担保包括但不限于可销售性, 对于特定情况的适应性和安全性
					无论何时，无论是否与软件有直接关联, 无论是否在合同或判决等书面文件中写明
					作者与版权拥有者都不为软件造成的直接或间接损失负责

					[c:red]如不同意本协议, 请勿使用本软件的任何服务[/c]"""),
				false);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		user.onLogout(server, this);
	}

	private void getHistory(int id, CharSequence filter, int off, int len) {
		ChatSubject u = server.getSubject(id);
		if (u instanceof ChatGroup g) {
			RingBuffer<Message> his = g.history;

			if (len == 0) {
				sendHistory(id, his.size(), Collections.emptyList());
				return;
			}

			if (len > 1000) len = 1000;

			ArrayList<Message> msgs = new ArrayList<>(Math.min(his.capacity(), len));

			off = his.size() - len - off;
			if (off < 0) {
				len += off;
				off = 0;
			}
			his.getSome(1, his.head(), his.tail(), msgs, off, len);

			filter = filter.toString();
			if (filter.length() > 0) {
				for (int i = msgs.size() - 1; i >= 0; i--) {
					if (!msgs.get(i).text.contains(filter)) {
						msgs.remove(i);
					}
				}
			}
			sendHistory(id, his.size(), msgs);
		} else {
				/*if (len == 0) {
					sendHistory(id, dao.getHistoryCount(id), Collections.emptyList());
					return;
				}

				MutableInt mi = new MutableInt();
				List<Message> msg = dao.getHistory(id, filter, off, len, mi);
				sendHistory(id, mi.getValue(), msg);*/
		}
	}
	private void deleteHistory(int uid, int deadline) {
		var u = server.getSubject(uid);
		if (!(u instanceof ChatUser)) return;

		/*ChatDAO.Result r = dao.delHistory(owner.id, uid);
		if (r.error != null) {
			sendAlert("无法清除历史纪录: " + r.error);
		}*/
	}
	// noop
	private void unloadHistory(int uid) {}
	//region Send
	protected final IntSet knownUsers = new IntSet();
	private final PacketBuffer pb = new PacketBuffer(10);
	private boolean shutdownInProgress;

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		if (!pb.isEmpty()) {
			var b = IOUtil.getSharedByteBuf();

			while (true) {
				b.clear();
				var take = pb.take(b);
				if (take == null) break;
				send(FRAME_BINARY, take);
			}
		} else if (shutdownInProgress) sendClose(ERR_OK, null);
	}

	public final void sendUserInfo(ChatSubject user) {
		knownUsers.add(user.id);

		ByteList b = IOUtil.getSharedByteBuf();
		user.serialize(b.put(P_USER_INFO));
		pb.offer(b);
	}

	public final void sendGroupChange(int groupId, ChatSubject user, boolean add) {
		if (add) knownUsers.add(user.id);

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(P_ROOM_STATE).putInt(groupId);
		if (add) {
			user.serialize(b);
		} else {
			b.putInt(user.id);
		}
		pb.offer(b);
	}

	public final void sendDataChanged(int userId) {
		if (!knownUsers.remove(userId)) return;

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

	public final void sendMessage(ChatSubject user, Message message, boolean sys) {
		if (message.text.length() > maxData - 10) throw new IllegalArgumentException("Message too long");
		int userId = user.id;
		knownUsers.add(userId);

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
	//endregion
}