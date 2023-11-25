package roj.net.mychat;

import roj.collect.*;
import roj.config.serial.ToSomeString;
import roj.util.ByteList;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Group extends AbstractUser {
	static final boolean initialSendUserInfo = false;

	public List<User> users = new SimpleList<>();

	public final RingBuffer<Message> history = new RingBuffer<>(100);

	private final MyHashSet<User> online = new MyHashSet<>();

	private final Int2IntMap userProps = new Int2IntMap();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public int owner;
	public final IntSet operators = new IntSet();

	public void joinGroup(User b) {
		users.add(b);
		onlineHook(b);
		if (online.isEmpty()) return;

		lock.readLock().lock();
		try {
			Message newJoin = new Message(id, "[Tr]group_join|" + b.id + "[/Tr]");
			for (User entry : online) {
				WSChat w = entry.worker;
				if (w == null) continue;
				w.sendMessage(this, newJoin, true);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void exitGroup(User b, int reason) {
		users.remove(b);
		offlineHook(b);
		if (online.isEmpty()) return;

		lock.readLock().lock();
		try {
			Message exit = new Message(id, "[Tr]group_exit|" + b.id + "|" + reason + "[/Tr]");
			for (User entry : online) {
				WSChat w = entry.worker;
				if (w == null || !operators.contains(entry.id)) continue;
				w.sendMessage(this, exit, true);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void postMessage(Context c, Message m, boolean sys) {
		history.ringAddLast(m);
		lock.readLock().lock();
		try {
			for (User entry : online) {
				WSChat w = entry.worker;
				if (w == null || entry.id == m.uid) continue;
				w.sendMessage(this, m, sys);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void onlineHook(User entry) {
		if (entry.worker == null) return;
		lock.writeLock().lock();
		try {
			online.add(entry);
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void offlineHook(User entry) {
		lock.writeLock().lock();
		try {
			online.remove(entry);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void put(ByteList b) {
		b.putInt(id).putUTF(name);
		b.putUTF("");
		b.putUTF(face);
		b.putUTF(desc);

		if (users.isEmpty()) {
			b.put((byte) 255);
			return;
		}

		if (initialSendUserInfo) {
			b.put(1);
			for (int i = 0; i < users.size(); i++) {
				users.get(i).put(b);
			}
		} else {
			b.put(0);
			for (int i = 0; i < users.size(); i++) {
				b.putInt(users.get(i).id);
			}
		}
	}

	@Override
	public void put(ToSomeString ser) {
		ser.key("users");
		ser.valueList();

		if (initialSendUserInfo) {
			for (int i = 0; i < users.size(); i++) {
				ser.valueMap();
				users.get(i).put(ser);
				ser.pop();
			}
			ser.pop();
		} else {
			for (int i = 0; i < users.size(); i++) {
				ser.value(users.get(i).id);
			}
			ser.pop();
			ser.key("raw");
			ser.value(1);
		}
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
	}

	@Override
	public void addMoreInfo(IntSet known) {
		if (users != null) {
			for (int i = 0; i < users.size(); i++) {
				known.add(users.get(i).id);
			}
		}
	}
}
