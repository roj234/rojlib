package roj.net.mychat;

import roj.collect.*;
import roj.config.serial.ToSomeString;
import roj.util.ByteList;

import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class User extends AbstractUser {
	public String username;

	public byte[] pass;
	public int passError;
	public long timeout;

	public final byte[] tokenSalt = new byte[16];
	public long tokenSeq;

	// 存储最近的N条未接收消息
	public RingBuffer<Message> c2c = new RingBuffer<>(100, false);

	// friends由friendsGroup反序列化时顺便填充
	// todo Viewing
	private final MyHashMap<String, IntSet> friendsGroup = new MyHashMap<>();
	public IntSet friends = new IntSet(), joinedGroups = new IntSet(), manageGroups = new IntSet();

	// flag为有关在线状态的,且共享给所有用户
	// flag2为其他状态位,如隐身,是否允许加好友等
	public int flag2;

	public TimeCounter attachCounter = new TimeCounter(60000);
	public AtomicInteger parallelUD = new AtomicInteger(4);

	// 对于其他用户的标志
	private final Int2IntMap userProp = new Int2IntMap();
	private final IntMap<String> pendingAdd = new IntMap<>();

	public volatile WSChat worker;

	public User() {

	}

	public void onLogon(Context c, WSChat w) {
		synchronized (this) {
			if (worker != null) {
				worker.sendExternalLogout("您已在另一窗口登录");
			}
			worker = w;
		}

		for (IntMap.Entry<String> friendNew : pendingAdd.selfEntrySet()) {
			String v = friendNew.getValue();
			if (v == null) continue;
			w.sendNewFriend(friendNew.getIntKey(), v);
			friendNew.setValue(null);
		}

		for (Message msg : c2c) {
			w.sendMessage(c.getUser(msg.uid), msg, false);
		}

		if ((flag & F_HIDDEN) == 0) {
			flag |= User.F_ONLINE;
			updateOnlineState(c);
		}

		for (OfInt itr = joinedGroups.iterator(); itr.hasNext(); ) {
			int i = itr.nextInt();
			Group u1 = (Group) c.getUser(i);
			u1.onlineHook(this);
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

		for (OfInt itr = joinedGroups.iterator(); itr.hasNext(); ) {
			int i = itr.nextInt();
			Group u1 = (Group) c.getUser(i);
			u1.offlineHook(this);
		}
	}

	public void onDataChanged(Context c) {
		for (OfInt itr = friends.iterator(); itr.hasNext(); ) {
			int uid = itr.nextInt();
			User u1 = (User) c.getUser(uid);
			if (u1.worker != null) {
				u1.worker.sendDataChanged(id);
			}
		}
	}

	public void setUserState(int state, String text) {

	}

	private void updateOnlineState(Context c) {
		for (OfInt itr = friends.iterator(); itr.hasNext(); ) {
			int uid = itr.nextInt();
			// 在线对其隐身
			if ((userProp.getOrDefaultInt(uid, 0) & P_HIDDEN) != 0) return;
			User u1 = (User) c.getUser(uid);
			if (u1.worker != null) {
				u1.worker.sendOnlineState(id, flag);
			}
		}
		if (worker != null) {
			worker.sendOnlineState(id, flag);
		}
	}

	public void postMessage(Context c, Message m, boolean s) {
		if (m.uid == id) return;
		if (!s && (flag & F_ANYCHAT) == 0 && !friends.contains(m.uid)) {
			c.getUser(m.uid).postMessage(c, new Message(id, "[Tr]not_friend[/Tr]"), true);
			return;
		}

		int oFlg = userProp.getOrDefaultInt(m.uid, 0);
		// 忽略ta发来的消息
		if ((oFlg & P_IGNORE) != 0) return;
		// 特别关心
		//if ((oFlg & P_CARE) != 0) {
		//
		//}

		WSChat w = worker;
		if (w != null) {
			w.sendMessage(c.getUser(m.uid), m, s);
		} else {
			c2c.ringAddLast(m);
		}
	}

	public String addFriend(int fr, String reason) {
		if (friends.contains(fr)) return "already_friend";
		int oFlg = userProp.getOrDefaultInt(fr, 0);
		if ((oFlg & P_BLOCKED) != 0) return "blocked";
		if (pendingAdd.containsKey(fr)) return "add_pending";
		if (pendingAdd.size() > 50) return "too_many_adding";

		WSChat w = worker;
		if (w != null) {
			w.sendNewFriend(fr, reason);
			pendingAdd.putInt(fr, null);
		} else {
			pendingAdd.putInt(fr, reason);
		}

		return null;
	}

	public static final byte F_ONLINE = 1, F_HIDDEN = 2, F_ANYCHAT = 4;
	// P_BLOCKED: checkBySelf
	// P_NOT_SEE_IT: checkBySelf
	// P_CARE: checkBySelf
	// P_IGNORE: checkByBoth
	// P_NOT_SEE_ME: checkByOther
	// P_HIDDEN: checkByOther
	public static final byte P_BLOCKED = 1, P_NOT_SEE_IT = 2, P_NOT_SEE_ME = 4, P_HIDDEN = 8, P_CARE = 16, P_IGNORE = 32;

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

	public boolean exist() {
		return pass != null;
	}
}
