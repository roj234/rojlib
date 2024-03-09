package roj.plugins.mychat;

import roj.collect.IntSet;
import roj.collect.MyHashSet;
import roj.collect.RingBuffer;
import roj.config.serial.ToSomeString;
import roj.util.ByteList;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Group extends AbstractUser {
	public MyHashSet<User> users = new MyHashSet<>();

	public final RingBuffer<Message> history = new RingBuffer<>(100);

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public void joinGroup(User b) {
		users.add(b);
		if (users.size()==1) return;

		lock.readLock().lock();
		try {
			Message newJoin = new Message(id, "[Tr]group_join|" + b.id + "[/Tr]");
			for (User entry : users) {
				WSChat w = entry.worker;
				if (w != null) w.sendMessage(this, newJoin, true);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void exitGroup(User b, int reason) {
		users.remove(b);
		if (users.isEmpty()) return;

		lock.readLock().lock();
		try {
			Message exit = new Message(id, "[Tr]group_exit|" + b.id + "|" + reason + "[/Tr]");
			for (User entry : users) {
				WSChat w = entry.worker;
				if (w != null) w.sendMessage(this, exit, true);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public void postMessage(Context c, Message m, boolean sys) {
		history.ringAddLast(m);
		lock.readLock().lock();
		try {
			for (User entry : users) {
				WSChat w = entry.worker;
				if (w == null || entry.id == m.uid) continue;
				w.sendMessage(this, m, sys);
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void put(ByteList b) {
		b.putInt(id).putUTF(name);
		b.putUTF("");
		b.putUTF(face);
		b.putUTF(desc);

		b.put(1);
		for (User u : users) {
			u.put(b);
		}
	}

	@Override
	public void put(ToSomeString ser) {
		ser.key("users");
		ser.valueList();
		for (User u : users) {
			ser.valueMap();
			u.put(ser);
			ser.pop();
		}
		ser.pop();
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
		for (User user : users) {
			known.add(user.id);
		}
	}
}