package roj.net.cross;

import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.concurrent.Shutdownable;
import roj.io.IOUtil;
import roj.net.ch.*;
import roj.net.ch.Pipe.CipherPipe;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.ch.osi.ClientLaunch;
import roj.net.cross.server.AEServer;
import roj.net.mss.SimpleEngineFactory;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.IOException;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2021/12/26 2:55
 */
abstract class IAEClient extends Constants implements Shutdownable, ChannelHandler {
	public static final SimpleEngineFactory client_factory = SimpleEngineFactory.client();

	final SelectorLoop loop;
	SocketAddress server;
	final SecureRandom rnd;

	final IntMap<Pipe> pipes;

	MyChannel handlers;

	boolean onlyOneMissed = true, login, shutdown;

	protected IAEClient(SelectorLoop loop) {
		this.rnd = new SecureRandom();
		this.pipes = new IntMap<>();
		this.loop = loop;
	}

	protected void logout(ChannelCtx ctx) {
		if (login) {
			login = false;
			LOGGER.info("退出登录");
			kickWithMessage(ctx, P___LOGOUT);
		}
	}

	@Override
	public final boolean wasShutdown() { return shutdown; }
	@Override
	public final void shutdown() {
		if (shutdown) return;
		shutdown = true;
		LOGGER.info("进入关闭进程, 请耐心等待...");

		if (login && handlers.isOpen()) {
			try {
				login = false;
				handlers.fireChannelWrite(IOUtil.getSharedByteBuf().put(P___LOGOUT));
				LockSupport.parkNanos(10_000_000);
			} catch (Exception ignored) {}
		}
		try {
			handlers.close();
		} catch (IOException ignored) {}

		LOGGER.info("已关闭");
	}

	final void prepareLogin(MyChannel ch) {
		ch.addLast("cipher", new MSSCipher(client_factory.get()))
		  .addLast("splitter", new VarintSplitter(3))
		  .addLast("timeout", new Timeout(CLIENT_TIMEOUT, 1000));
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;
		if (id == Timeout.READ_TIMEOUT) {
			if (onlyOneMissed) {
				onlyOneMissed = false;
				ctx.channelWrite(IOUtil.getSharedByteBuf().put(P___HEARTBEAT));
				event.setResult(Event.RESULT_DENY);
			} else {
				LOGGER.warn("读取超时!");
			}
		} else if (id == Timeout.WRITE_TIMEOUT) {
			LOGGER.warn("flush超时!");
			ctx.close();
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		if (!pipes.isEmpty()) {
			Int2IntMap count = new Int2IntMap();

			for (Iterator<Pipe> itr = pipes.values().iterator(); itr.hasNext(); ) {
				Pipe pair = itr.next();
				PipeInfoClient att = (PipeInfoClient) pair.att;

				boolean clean = pair.isUpstreamEof() | pair.isDownstreamEof();
				if (pair.idleTime > PIPE_IDLE_MAX) {
					clean |= count.getEntryOrCreate(att.clientId, 1).v++ > PIPE_IDLE_MIN;
				}

				if (clean) {
					itr.remove();
					pair.close();
					LOGGER.info("关闭管道 {}, {}", att, pair.isUpstreamEof()?"remote_eof":pair.isDownstreamEof()?"local_eof":"idle");
				}
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (login) LOGGER.info("连接中止");

		for (Pipe pair : pipes.values()) {
			try {
				pair.close();
			} catch (IOException ignored) {}
		}
		pipes.clear();
		shutdown();
	}

	final void sendLoginPacket(MyChannel ch, DynByteBuf packet) {
		ByteList data = new ByteList(packet.toByteArray());
		ch.addLast("auth_after", new ChannelHandler() {
			@Override
			public void channelOpened(ChannelCtx ctx) throws IOException {
				ctx.channelWrite(data);
				compress(ctx.channel());
			}
			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				handleLoginPacket(ctx, (DynByteBuf) msg);
			}
		}).addLast("client", this);
	}
	abstract void handleLoginPacket(ChannelCtx ctx, DynByteBuf rb) throws IOException;

	final void asyncPipeLogin(int pipeId, byte[] key, Consumer<Pipe> success) throws IOException {
		if (AEServer.server != null) {
			AEServer.server.addLocalPipe(pipeId, key, success);
			return;
		}

		ClientLaunch bs = ClientLaunch.tcp().loop(loop).connect(server);
		bs.channel()
		  .addLast("cipher", new MSSCipher(client_factory.get()))
		  .addLast("splitter", VarintSplitter.twoMbVLUI())
		  .addLast("timeout", new Timeout(2000))
		  .addLast("pipe_handler", new ChannelHandler() {
			  @Override
			  public void channelOpened(ChannelCtx ctx) throws IOException {
				  ctx.channelWrite(IOUtil.getSharedByteBuf().put(PPS_PIPE_LOGIN).putInt(pipeId));

				  Pipe p = new CipherPipe(key);
				  p.setUp(ctx.channel());
				  success.accept(p);
			  }
		  });
		bs.launch();
	}

	@Override
	public String toString() { return handlers.remoteAddress().toString(); }
}