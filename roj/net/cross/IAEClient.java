package roj.net.cross;

import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.concurrent.FastLocalThread;
import roj.concurrent.Shutdownable;
import roj.crypt.KeyFile;
import roj.io.IOUtil;
import roj.net.ch.*;
import roj.net.ch.Pipe.CipherPipe;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.mss.JKey;
import roj.net.mss.MSSEngine;
import roj.net.mss.SimpleEngineFactory;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;

/**
 * todo 2-side mode
 * @author Roj233
 * @since 2021/12/26 2:55
 */
abstract class IAEClient extends FastLocalThread implements Shutdownable, ChannelHandler {
	static SimpleEngineFactory factory;

	static {
		File file = new File(System.getProperty("ae.keyPath", "ae_client.key"));
		factory = SimpleEngineFactory.client();
		if (file.isFile()) {
			try {
				PublicKey rsa = KeyFile.getInstance("RSA").getPublic(file);
				if (rsa != null) factory.switches(MSSEngine.PSC_ONLY).psc(0, new JKey(rsa));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// 客户端最低保留频道
	static final int MIN_CHANNEL_COUNT = 2;
	// 5分钟
	static final int CHANNEL_IDLE_TIMEOUT = 300_000;

	final SelectorLoop loop;
	final SocketAddress server;
	final SecureRandom rnd;

	final String id, token;

	final IntMap<Pipe> socketsById;
	List<Pipe>[] free;

	MyChannel handlers;

	boolean received = true, logout, shutdown;

	protected IAEClient(SocketAddress server, String id, String token) {
		this.server = server;
		this.id = id;
		this.token = token;
		this.rnd = new SecureRandom();
		this.socketsById = new IntMap<>();
		this.loop = new SelectorLoop(this, "AE 客户端IO", 1, 30000, 1);
	}

	protected void logout(ChannelCtx ctx) throws IOException {
		if (!logout) {
			logout = true;
			print("退出登录");
			ChannelCtx.bite(ctx, (byte) P_LOGOUT);
			ctx.close();
		}
	}

	@Override
	public final void shutdown() {
		if (shutdown) return;
		shutdown = true;
		print("进入关闭进程, 请耐心等待...");

		if (!logout && handlers.isOpen()) {
			try {
				handlers.fireChannelWrite(ByteList.wrap(new byte[] {P_LOGOUT}));
				logout = true;
				LockSupport.parkNanos(10_000_000);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			handlers.close();
		} catch (IOException ignored) {}

		loop.shutdown();

		while (isAlive()) {
			LockSupport.unpark(this);
		}
		print("已关闭");
	}

	@Override
	public final boolean wasShutdown() {
		return shutdown;
	}

	@Override
	public final void run() {
		try {
			MyChannel ctx = MyChannel.openTCP()
				.addLast("cipher", new MSSCipher(factory.get()))
				.addLast("splitter", new VarintSplitter(3))
				.addLast("compress", new Compress(1024, 127, 1024, -1))
				.addLast("timeout", new Timeout(CLIENT_TIMEOUT, 5000));
			prepareLogin(ctx);
			ctx.addLast("handler", this);
			ctx.connect(server);
			handlers = ctx;
			loop.register(ctx, null, SelectionKey.OP_CONNECT);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		while (!shutdown) LockSupport.park(this);
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;
		if (id == Timeout.READ_TIMEOUT) {
			if (received) {
				received = false;
				ChannelCtx.bite(ctx, (byte) P_HEARTBEAT);
				event.setResult(Event.RESULT_DENY);
			} else {
				print("读取超时!");
			}
		} else if (id == Timeout.WRITE_TIMEOUT) {
			print("写出超时!");
			ctx.close();
		}
	}

	protected abstract void prepareLogin(MyChannel ctx);

	@Override
	public final String toString() {
		return id;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (!socketsById.isEmpty()) {
			DynByteBuf rb = ctx.allocate(true, 1024);
			Int2IntMap count = new Int2IntMap();

			for (Iterator<Pipe> itr = socketsById.values().iterator(); itr.hasNext(); ) {
				Pipe pair = itr.next();
				SpAttach att = (SpAttach) pair.att;
				List<Pipe> pairs = free[att.portId];
				boolean clean = pair.isUpstreamEof();
				if (!clean && pair.idleTime > CHANNEL_IDLE_TIMEOUT) {
					clean = count.getEntryOrCreate(att.clientId, 1).v++ > MIN_CHANNEL_COUNT;
				}
				if (clean) {
					itr.remove();

					pair.close();
					if (!pairs.isEmpty()) pairs.remove(pair);

					rb.put((byte) P_CHANNEL_CLOSE).putInt(att.channelId);

					print("超时/EOF 关闭 #" + att.channelId);
				}
			}

			if (rb.isReadable()) {
				ctx.channelWrite(rb);
			}
			ctx.reserve(rb);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (!logout) {
			print("连接中止");
		}

		for (Pipe pair : socketsById.values()) {
			try {
				pair.close();
			} catch (IOException ignored) {}
		}
		socketsById.clear();
		shutdown();
	}

	static void onError(DynByteBuf buf, Throwable e) {
		int bc;
		if (buf.isReadable() && (bc = (0xFF & buf.get(buf.rIndex)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
			print("服务错误 " + ERROR_NAMES[bc]);
		} else if (e != null) {
			print("异常 " + e.getMessage());
			if (e.getClass() != IOException.class) e.printStackTrace();
		} else {
			print("未知数据包: " + buf);
		}
	}

	final void asyncPipeLogin(long pipeId, byte[] ciphers, Consumer<Pipe> callback) throws IOException {
		MyChannel ctx = MyChannel.openTCP()
								 .addLast("cipher", new MSSCipher(factory.get()))
								 .addLast("splitter", new VarintSplitter(3))
								 .addLast("compress", new Compress(127, 127, 0, -1))
								 .addLast("timeout", new Timeout(2000, 2000))
								 .addLast("auth", new AEAuthenticator(IOUtil.getSharedByteBuf().putLong(pipeId).toByteArray(), PS_LOGIN_PIPE))
								 .addLast("pipe_transformer", new ChannelHandler() {
											   @Override
											   public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
												   Pipe pair = new CipherPipe(ciphers);
												   SpAttach att = new SpAttach();
												   pair.att = att;
												   socketsById.putInt(att.channelId = (int) (pipeId >>> 32), pair);

												   pair.setUp(ctx.channel());
												   callback.accept(pair);
											   }
										   });
		ctx.connect(server);

		try {
			loop.register(ctx, null, SelectionKey.OP_CONNECT|SelectionKey.OP_READ);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	static final Object CheckServerAlive = new Object();
}