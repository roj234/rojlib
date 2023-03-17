package roj.net.cross.server;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.RingBuffer;
import roj.config.data.CMapping;
import roj.net.ch.Pipe;
import roj.util.ByteList;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * @author Roj233
 * @since 2022/1/24 3:18
 */
public final class Room {
	Client master;
	String id, token;

	String motdString;
	byte[] motd, portMap;

	// 房主的直连地址
	byte[] upnpAddress;

	final IntMap<Client> clients = new IntMap<>();
	int index;

	// 重置锁
	private final MyBitSet resetLock;
	private int resetOffset, lastRem;

	boolean isPending(int k) {
		return resetLock.contains(k - resetOffset);
	}

	void addPending(int k) {
		if (resetLock.size() == 0) {
			int min = Integer.MAX_VALUE;
			int rm = AEServer.server.remain.get();
			if (Math.abs(rm - lastRem) > 10) {
				lastRem = rm;
				synchronized (master.pipes) {
					for (PipeGroup pg : master.pipes.values()) {
						if (pg.id < min) min = pg.id;
					}
				}
				resetOffset = min;
			}
		}
		synchronized (resetLock) {
			resetLock.add(k - resetOffset);
		}
	}

	void removePending(int k) {
		synchronized (resetLock) {
			resetLock.remove(k - resetOffset);
		}
	}

	// 配置项
	public boolean locked;
	public final long creation;

	// region 聊天相关
	public boolean muted, p2pChat;
	// 启用聊天功能的
	final MyBitSet chatEnabled;
	// 禁言的
	final MyBitSet chatBanned;
	// 最近的消息
	final RingBuffer<Message> recentMessage;
	// 可用的消息[缓冲]
	final ArrayList<Message> buffer;
	// endregion

	static final class Message {
		Client from;
		final ByteList data;

		Message(Client from, int msgLen) {
			this.from = from;
			this.data = new ByteList(msgLen);
		}
	}

	public Room(String id, Client owner, String token) {
		this.master = owner;
		this.id = id;
		this.token = token;
		this.clients.putInt(0, owner);
		this.index = 1;
		this.creation = System.currentTimeMillis() / 1000;
		this.resetLock = new MyBitSet();
		owner.room = this;

		this.chatEnabled = new MyBitSet();
		this.chatBanned = new MyBitSet();
		this.recentMessage = new RingBuffer<>(ChatUtil.RECENT_MESSAGE_COUNT);
		this.buffer = new ArrayList<>(10);
	}

	public void close() {
		token = null;
		synchronized (clients) {
			clients.clear();
		}
	}

	public void kick(int id) {
		synchronized (clients) {
			clients.remove(id);
		}
	}

	public CMapping serialize() {
		long up = 0, down = 0;
		if (!master.pipes.isEmpty()) {
			synchronized (master.pipes) {
				for (PipeGroup group : master.pipes.values()) {
					Pipe ref = group.pairRef;
					if (ref != null) {
						up += ref.downloaded;
						down += ref.uploaded;
					}
				}
			}
		}

		CMapping json = new CMapping();
		json.put("id", id);
		json.put("pass", token);
		json.put("time", creation);
		json.put("up", up);
		json.put("down", down);
		json.put("users", clients.size());
		json.put("index", index);
		json.put("motd", motdString);
		json.put("master", master == null ? "" : master.handler.remoteAddress().toString());
		return json;
	}

	// todo
	public RingBuffer<Message> getRecentMessage() {
		return recentMessage;
	}

	public void sendMessage(Client from, int msgLen, ByteBuffer rb) {
		synchronized (recentMessage) {
			Message msg;
			if (!buffer.isEmpty()) {
				msg = buffer.remove(buffer.size() - 1);
				msg.data.ensureCapacity(msgLen);
				msg.from = from;
			} else {
				msg = new Message(from, msgLen);
			}
			rb.get(msg.data.list);

			Message down = recentMessage.ringAddLast(msg);
			if (down != null) {
				buffer.add(down);
			}
		}
	}
}
