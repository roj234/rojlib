package roj.plugins.mychat;

import roj.collect.Int2IntMap;
import roj.collect.IntSet;
import roj.collect.RingBuffer;
import roj.config.serial.ToSomeString;
import roj.util.ByteList;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class User extends AbstractUser {
	// 存储最近的N条未接收消息
	public RingBuffer<Message> c2c = new RingBuffer<>(100, false);

	Group globalGroupMy;

	public AtomicInteger parallelUD = new AtomicInteger(2);

	// 对于其他用户的标志
	private final Int2IntMap userProp = new Int2IntMap();

	public volatile WSChat worker;

	public User() {}

	public void onLogon(Context c, WSChat w) {
		synchronized (this) {
			if (worker != null) {
				worker.sendExternalLogout("您已在另一窗口登录");
			}
			worker = w;
		}

		for (Message msg : c2c) {
			w.sendMessage(c.getUser(msg.uid), msg, false);
		}

		if ((flag & F_HIDDEN) == 0) {
			flag |= User.F_ONLINE;
			updateOnlineState(c);
		}
	}

	public void onLogout(Context c, WSChat w) {
		synchronized (this) {
			if (w != worker) return;
			worker = null;
		}

		if ((flag & F_HIDDEN) == 0) {
			flag &= ~User.F_ONLINE;
			updateOnlineState(c);
		}
	}

	public void onDataChanged(Context c) {
		for (User user : globalGroupMy.users) {
			WSChat w = user.worker;
			if (w != null) {
				w.sendDataChanged(id);
			}
		}
	}

	private void updateOnlineState(Context c) {
		/*for (User user : globalGroupMy.users) {
			WSChat w = user.worker;
			if (w != null) {
				w.sendOnlineState(id, flag);
			}
		}*/

		if (worker != null) {
			worker.sendOnlineState(id, flag);
		}
	}

	public void postMessage(Context c, Message m, boolean s) {
		if (m.uid == id) return;

		int oFlg = userProp.getOrDefaultInt(m.uid, 0);
		if ((oFlg & P_IGNORE) != 0) return;

		WSChat w = worker;
		if (w != null) {
			w.sendMessage(c.getUser(m.uid), m, s);
		} else {
			c2c.ringAddLast(m);
		}
	}

	public static final byte F_ONLINE = 1, F_HIDDEN = 2;
	public static final byte P_IGNORE = 4;

	@Override
	public void put(ByteList b) {
		b.putInt(id).putUTF(name);
		b.putUTF("");
		b.putUTF(face);
		b.putUTF(desc);
		b.put(flag);
	}

	@Override
	public void put(ToSomeString ser) {
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
	}

	@Override
	public void addMoreInfo(IntSet known) {}
}