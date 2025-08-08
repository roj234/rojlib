package roj.plugins.mychat;

import roj.collect.HashSet;
import roj.collect.RingBuffer;
import roj.util.ByteList;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
final class ChatGroup extends ChatSubject {
	HashSet<ChatUser> users = new HashSet<>();
	final RingBuffer<Message> history = new RingBuffer<>(100);
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	@Override
	public void serialize(ByteList b) {
		b.putInt(id).putUTF(name);
		b.putUTF("");
		b.putUTF(face);
		b.putUTF(desc);

		b.put(1); // or use 0 to serialize UID only
		for (ChatUser u : users) u.serialize(b);
	}

	public void joinGroup(ChatUser user) {
		users.add(user);
		if (users.size() == 1) return;
		broadcastMessage(new Message(id, "[Tr]group_join|"+user.id+"[/Tr]"), true);
	}
	public void exitGroup(ChatUser user, int reason) {
		users.remove(user);
		if (users.isEmpty()) return;
		broadcastMessage(new Message(id, "[Tr]group_exit|"+user.id+"|"+reason+"[/Tr]"), true);
	}
	public void sendMessage(ChatManager c, Message m, boolean sys) {broadcastMessage(m, sys);}

	private void broadcastMessage(Message message, boolean system) {
		lock.readLock().lock();
		try {
			history.ringAddLast(message);

			for (ChatUser user : users) {
				ChatWorker w = user.worker;
				if (user.id != message.uid && w != null) w.sendMessage(this, message, system);
			}
		} finally {
			lock.readLock().unlock();
		}
	}
}