package roj.net;

import roj.util.Helpers;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj233
 * @since 2022/6/14 20:58
 */
final class ServerLaunchTcp extends ServerLaunch implements Selectable {
	private final String name;
	private final ServerSocketChannel tcp;
	private SelectionKey key;

	private int rcvBuf = 1536;
	private AtomicInteger maxConn;

	ServerLaunchTcp(String name) throws IOException {
		this.name = name;
		tcp = ServerSocketChannel.open();
		tcp.configureBlocking(false);
	}

	public <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		if (k == TCP_MAX_CONNECTION) maxConn = new AtomicInteger((Integer) v);
		else if (k == TCP_RECEIVE_BUFFER) rcvBuf = (Integer) v;
		else if (k == StandardSocketOptions.SO_REUSEPORT) Net.setReusePort(tcp, (boolean) v);
		else tcp.setOption(k, v);
		return this;
	}
	public <T> T option(SocketOption<T> k) throws IOException {
		if (k == TCP_MAX_CONNECTION) return Helpers.cast(maxConn == null ? -1 : maxConn.get());
		else if (k == TCP_RECEIVE_BUFFER) return Helpers.cast(rcvBuf);
		else return tcp.getOption(k);
	}

	public ServerLaunch bind(SocketAddress a, int backlog) throws IOException { tcp.bind(a, backlog); return this; }
	public SocketAddress localAddress() throws IOException { return tcp.getLocalAddress(); }

	public ServerLaunch launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("no initializator");

		if (name != null) SHARED.put(name, this);
		loop().register(this, null, SelectionKey.OP_ACCEPT);
		return this;
	}

	public final boolean isOpen() { return tcp.isOpen(); }
	public final void close() throws IOException {
		if (name != null) SHARED.remove(name, this); tcp.close(); }

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
			var sc = tcp.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			var ch = new TcpChImpl(sc, rcvBuf);
			initialize(ch);
		}
	}
	private void acceptLimited() throws IOException {
		while (true) {
			if (maxConn.getAndDecrement() <= 0) {
				key.interestOps(0);
				maxConn.getAndIncrement();
				return;
			}

			var sc = tcp.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			var ch = new TcpChImpl(sc, rcvBuf) {
				@Override
				protected void closeHandler() throws IOException {
					super.closeHandler();

					maxConn.getAndIncrement();
					SelectionKey key1 = ServerLaunchTcp.this.key;
					if (key1.isValid() && key1.interestOps() == 0) key1.interestOps(SelectionKey.OP_ACCEPT);
				}
			};

			initialize(ch);
		}
	}

	private void initialize(TcpChImpl ch) throws IOException {
		initializator.accept(ch);
		if (ch.isOpen()) {
			ch.open();
			if (ch.isOpen())
				loop.register(ch, null);
		}
	}

	@Override
	public void addTCPConnection(MyChannel channel) throws IOException {
		initializator().accept(channel);
		channel.fireOpen();
		channel.readActive();
	}
}