package roj.plugins.mychat;

import roj.collect.Int2IntMap;
import roj.collect.RingBuffer;
import roj.config.serial.CRawString;
import roj.config.serial.ToJson;
import roj.http.server.Request;
import roj.text.DateTime;
import roj.util.ByteList;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
final class ChatUser extends ChatSubject {
	// 存储最近的N条未接收消息
	private final RingBuffer<Message> offlineMessages = RingBuffer.lazy(1000);
	public final AtomicInteger uploadTasks = new AtomicInteger(2);

	// 对于其他用户的标志
	private final Int2IntMap userProp = new Int2IntMap();

	public volatile ChatWorker worker;
	long stateTimestamp;

	public ChatUser() {}

	public void onLogin(ChatManager c, ChatWorker w, Request req) {
		synchronized (this) {
			if (worker != null) {
				worker.sendExternalLogout("您已在他处登录<br />" +
						"IP: " + req.connection().remoteAddress() + "<br />" +
						"UA: " + req.header("User-Agent") + "<br />" +
						"时间: " + DateTime.toLocalTimeString(System.currentTimeMillis()));
			}
			worker = w;
		}

		for (Message msg : offlineMessages) {
			w.sendMessage(c.getSubject(msg.uid), msg, false);
		}

		if ((flag & F_HIDDEN) == 0) {
			flag |= ChatUser.F_ONLINE;
			updateOnlineState(c, w);
		}
	}
	public void onLogout(ChatManager c, ChatWorker w) {
		synchronized (this) {
			if (w != worker) return;
			worker = null;
		}

		if ((flag & F_HIDDEN) == 0) {
			flag &= ~ChatUser.F_ONLINE;
			updateOnlineState(c, w);
		}
	}

	public void onDataChanged(ChatManager c) {
		int limit = 1000;
		for (Integer i : worker.knownUsers) {
			if (c.getSubject(i) instanceof ChatUser user) {
				ChatWorker w = user.worker;
				if (w != null) {
					w.sendDataChanged(id);
				}
				if (--limit <= 0) return;
			}
		}
	}

	private void updateOnlineState(ChatManager c, ChatWorker w1) {
		int limit = 1000;
		for (Integer i : w1.knownUsers) {
			if (c.getSubject(i) instanceof ChatUser user) {
				ChatWorker w = user.worker;
				if (w != null) {
					w.sendOnlineState(id, flag);
				}
				if (--limit <= 0) return;
			}
		}
	}

	public void sendMessage(ChatManager c, Message m, boolean s) {
		int myFlag = userProp.getOrDefaultInt(m.uid, 0);
		if ((myFlag & P_IGNORE) != 0) return;

		ChatWorker w = worker;
		if (w != null) {
			w.sendMessage(c.getSubject(m.uid), m, s);
		} else {
			if (m.uid == id) return;

			offlineMessages.ringAddLast(m);
		}
	}

	public static final byte F_ONLINE = 1, F_HIDDEN = 2, P_IGNORE = 4;

	@Override
	public void serialize(ByteList b) {
		b.putInt(id).putUTF(name).putUTF("").putUTF(face).putUTF(desc);
	}

	public final CRawString put() {
		ToJson ser = new ToJson();
		ser.valueMap();

		ser.key("id");
		ser.value(id);

		ser.key("name");
		ser.value(name);

		ser.key("username");
		ser.value("");

		ser.key("face");
		ser.value(face);

		ser.key("desc");
		ser.value(desc);

		ser.key("online");
		ser.value(flag);

		ser.pop();
		return new CRawString(ser.getValue());
	}
}