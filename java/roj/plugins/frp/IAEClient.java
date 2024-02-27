package roj.plugins.frp;

import roj.collect.MyHashSet;
import roj.concurrent.Shutdownable;
import roj.io.IOUtil;
import roj.net.ch.*;
import roj.net.handler.MSSCipher;
import roj.net.handler.Pipe2;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.net.mss.SimpleEngineFactory;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2021/12/26 2:55
 */
abstract class IAEClient extends Constants implements Shutdownable {
	static final SimpleEngineFactory client_factory = SimpleEngineFactory.client();

	char[] portMap;
	int udpPortMap;

	final SelectorLoop loop;
	SocketAddress server;

	final MyHashSet<Pipe2> pipes = new MyHashSet<>();

	MyChannel handlers;

	boolean onlyOneMissed = true, login, shutdown;

	protected IAEClient(SelectorLoop loop) {this.loop = loop;}
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
		LOGGER.info("正在关闭");

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
	}

	final void prepareLogin(MyChannel ch) {
		ch.addLast("cipher", new MSSCipher(client_factory.get()))
		  .addLast("splitter", new VarintSplitter(3))
		  .addLast("timeout", new Timeout(CLIENT_TIMEOUT, 1000));
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		String id = event.id;
		if (id.equals(Timeout.READ_TIMEOUT)) {
			if (onlyOneMissed) {
				onlyOneMissed = false;
				ctx.channelWrite(IOUtil.getSharedByteBuf().put(P___HEARTBEAT).putLong(System.currentTimeMillis()));
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
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (login) LOGGER.info("连接中止");

		synchronized (pipes) {
			for (Pipe2 pair : pipes) pair.close();
			pipes.clear();
		}
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

	@Override
	public String toString() { return handlers.remoteAddress().toString(); }

	final class TcpTimeout implements ChannelHandler {
		private final PipeInfoClient att;
		private boolean opened;
		private int idle;
		TcpTimeout(PipeInfoClient att) {this.att = att;}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			idle = 0;
			ctx.channelRead(msg);
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws Exception {
			if (++idle > PIPE_IDLE_MAX) ctx.close();
		}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			LOGGER.debug("本地连接成功 {}", att);
			opened = true;
			ctx.channelOpened();
		}

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			LOGGER.error("本地连接异常 {}", ex, att);
			ctx.close();
		}

		@Override
		public void channelClosed(ChannelCtx ctx) {
			synchronized (pipes){pipes.remove(att.pipeId);}
			if (!opened) LOGGER.debug("本地连接失败 {}", att);
			else {
				String reason;
				if (idle > PIPE_IDLE_MAX) reason = "idle";
				else if (Pipe2.CURRENT_WRITER.get() != null) reason = "remote_eof";
				else reason = "local_eof";

				LOGGER.info("关闭管道 {}, {}", att, reason);
			}
		}
	}
}