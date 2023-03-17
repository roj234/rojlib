package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.PrefixFactory;
import roj.concurrent.Shutdownable;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.net.ch.MyChannel;
import roj.net.ch.SelectorLoop;
import roj.net.ch.ServerSock;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.mss.MSSEngine;
import roj.util.EmptyArrays;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @since 2021/8/17 22:17
 */
public class AEServer implements Shutdownable, Consumer<ServerSock> {
	static AEServer server;

	// 20分钟
	static final int PIPE_TIMEOUT = 1200_000;

	byte[] info = EmptyArrays.BYTES;

	public void setMOTD(String motd) {
		info = motd.getBytes(StandardCharsets.UTF_8);
	}

	ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

	int pipeId;
	ConcurrentHashMap<Integer, PipeGroup> pipes = new ConcurrentHashMap<>();
	final Random rnd;

	private final TaskPool asyncPool;
	final SelectorLoop man;
	private final Supplier<MSSEngine> factory;

	final ServerSock s;
	final AtomicInteger remain;

	public AEServer(InetSocketAddress addr, int conn, Supplier<MSSEngine> factory) throws IOException {
		server = this;

		s = ServerSock.openTCP().bind(addr.getAddress(), addr.getPort(), conn).setOption(StandardSocketOptions.SO_REUSEADDR, true);

		this.remain = new AtomicInteger(conn);
		this.factory = factory;
		this.rnd = new SecureRandom();

		int thr = Runtime.getRuntime().availableProcessors();
		String p = System.getProperty("AE.client_selectors");
		if (p != null) {
			try {
				thr = Integer.parseInt(p);
			} catch (NumberFormatException ignored) {}
		}
		this.man = new SelectorLoop(this, "AE 服务器IO", 0, thr, 60000, 100);

		thr = 6;
		p = System.getProperty("AE.executors");
		if (p != null) {
			try {
				thr = Integer.parseInt(p);
			} catch (NumberFormatException ignored) {}
		}
		this.asyncPool = new TaskPool(1, thr, 1, 1, 120000, new PrefixFactory("Executor"));
		this.asyncPool.setRejectPolicy(TaskPool::newThreadPolicy);
	}

	public void asyncExecute(ITask task) {
		asyncPool.pushTask(task);
	}

	@Override
	public void accept(ServerSock sock) {
		if (!sock.isOpen()) {
			shutdown();
			return;
		}
		try {
			MyChannel ctx = sock.accept();

			if (remain.decrementAndGet() <= 0) {
				remain.getAndIncrement();
				ctx.close();
			} else {
				if (rooms.isEmpty()) pipeId = 0;

				Client client = new Client();

				initSocketPref(ctx);

				ctx.addLast("cipher", new MSSCipher(factory.get()))
				   .addLast("splitter", new VarintSplitter(3))
				   .addLast("compress", new Compress(1024, 127, 1024, -1))
				   .addLast("timeout", new Timeout(SERVER_TIMEOUT, 5000))
				   .addLast("state", Handshake.HANDSHAKE)
				   .addLast("handler", client);
				ctx.attachment(Client.CLIENT, client);
				ctx.open();

				man.register(ctx, null);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public boolean canCreateRoom = true, canJoinRoom = true;

	protected int login(Client worker, boolean owner, String id, String token) {
		Room room = rooms.get(id);
		if (owner != (room == null)) return PS_ERROR_AUTH;
		if (room == null) {
			if (!canCreateRoom) return PS_ERROR_SYSTEM_LIMIT;
			rooms.put(id, new Room(id, worker, token));
			return -1;
		}
		if (!canJoinRoom || room.locked) return PS_ERROR_SYSTEM_LIMIT;
		if (!token.equals(room.token)) return PS_ERROR_AUTH;
		IntMap<Client> clients = room.clients;
		synchronized (clients) {
			if (clients.size() == 1) room.index = 1;
			clients.putInt(worker.clientId = room.index++, worker);
		}
		worker.room = room;
		return -1;
	}

	@Override
	public boolean wasShutdown() {
		return shutdown;
	}

	@Override
	public void shutdown() {
		if (shutdown) return;
		try {
			s.close();
		} catch (IOException ignored) {}

		asyncPool.shutdown();
		man.shutdown();

		for (Room room : rooms.values()) {
			room.token = null;
			room.master = null;
			synchronized (room.clients) {
				for (Client w : room.clients.values()) {
					try {
						w.handler.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			room.close();
		}

		LockSupport.parkNanos(1000_000);
		shutdown = true;

		System.out.println("服务器关闭");
	}

	boolean shutdown;

	long lastCheckTime;

	void pipeTimeoutHandler() {
		long time = System.currentTimeMillis();
		if (time - lastCheckTime < 1000) return;
		lastCheckTime = time;
		// noinspection all
		for (Iterator<PipeGroup> itr = pipes.values().iterator(); itr.hasNext(); ) {
			PipeGroup group = itr.next();
			if (group.timeout > time) {
				itr.remove();
			}
		}
	}

	public void start() throws IOException {
		s.register(man, this);
	}
}