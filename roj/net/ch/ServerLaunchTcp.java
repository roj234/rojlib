package roj.net.ch;

import roj.util.Helpers;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj233
 * @since 2022/6/14 20:58
 */
final class ServerLaunchTcp extends ServerLaunch implements Selectable {
	private final ServerSocketChannel tcp;
	private SelectionKey key;

	private int rcvBuf;
	private AtomicInteger maxConn;

	ServerLaunchTcp() throws IOException {
		tcp = ServerSocketChannel.open();
		tcp.configureBlocking(false);
	}

	public <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		if (k == TCP_MAX_ALIVE_CONNECTION) maxConn = new AtomicInteger((Integer) v);
		else if (k == CHANNEL_RECEIVE_BUFFER) rcvBuf = (Integer) v;
		else tcp.setOption(k, v);
		return this;
	}
	public <T> T option(SocketOption<T> k) throws IOException {
		if (k == TCP_MAX_ALIVE_CONNECTION) return Helpers.cast(maxConn == null ? -1 : maxConn.get());
		else if (k == CHANNEL_RECEIVE_BUFFER) return Helpers.cast(rcvBuf);
		else return tcp.getOption(k);
	}

	@Override
	public ServerLaunch launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("no initializator");

		tcp.bind(addr, backlog);
		loop().register(this, null, SelectionKey.OP_ACCEPT);
		return this;
	}

	public final boolean isOpen() { return tcp.isOpen(); }
	public final void close() throws IOException { tcp.close(); }

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		if (key != null && tcp.keyFor(sel) != key) key.cancel();
		key = tcp.register(sel, ops, att);
	}

	@Override
	public void selected(int readyOps) throws Exception {
		if (maxConn == null) acceptLimitless();
		else acceptLimited();
	}

	private void acceptLimitless() throws IOException {
		while (true) {
			SocketChannel sc = tcp.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			MyChannel ch = new TcpChImpl(sc, rcvBuf);

			initializator.accept(ch);
			if (ch.isOpen()) {
				ch.open();
				loop.register(ch, null);
			}
		}
	}
	private void acceptLimited() throws IOException {
		while (true) {
			if (maxConn.getAndDecrement() <= 0) {
				key.interestOps(0);
				maxConn.getAndIncrement();
				return;
			}

			SocketChannel sc = tcp.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			MyChannel ch = new TcpChImpl(sc, rcvBuf) {
				@Override
				protected void closeHandler() throws IOException {
					super.closeHandler();

					maxConn.getAndIncrement();
					SelectionKey key1 = ServerLaunchTcp.this.key;
					if (key1.isValid() && key1.interestOps() == 0) key1.interestOps(SelectionKey.OP_ACCEPT);
				}
			};

			initializator.accept(ch);
			if (ch.isOpen()) {
				ch.open();
				loop.register(ch, null);
			}
		}
	}
}
