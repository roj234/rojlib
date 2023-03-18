package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.Shutdownable;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.ch.osi.ServerLaunch;
import roj.net.mss.MSSEngine;
import roj.util.ArrayCache;

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
public class AEServer implements Shutdownable, Consumer<MyChannel> {
	static AEServer server;

	// 20分钟
	static final int PIPE_TIMEOUT = 1200_000;

	byte[] info = ArrayCache.BYTES;

	public void setMOTD(String motd) {
		info = motd.getBytes(StandardCharsets.UTF_8);
	}

	ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

	int pipeId;
	ConcurrentHashMap<Integer, PipeGroup> pipes = new ConcurrentHashMap<>();
	final Random rnd;

	private final TaskPool asyncPool;
	final ServerLaunch man;
	private final Supplier<MSSEngine> factory;

	final AtomicInteger remain;

	public AEServer(InetSocketAddress addr, int conn, Supplier<MSSEngine> factory) throws IOException {
		server = this;

		this.man = ServerLaunch.tcp().listen_(addr, conn).option(StandardSocketOptions.SO_REUSEADDR, true).initializator(this);

		this.remain = new AtomicInteger(conn);
		this.factory = factory;
		this.rnd = new SecureRandom();

		this.asyncPool = TaskPool.ParallelPool();
		this.asyncPool.setRejectPolicy(TaskPool::newThreadPolicy);
	}

	public void asyncExecute(ITask task) {
		asyncPool.pushTask(task);
	}

	@Override
	public void accept(MyChannel ctx) {
		try {
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
			man.close();
		} catch (IOException ignored) {}

		asyncPool.shutdown();

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
		man.launch();
		man.getLoop().setOwner(this);
	}
}