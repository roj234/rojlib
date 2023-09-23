package roj.net.ch;

import roj.util.Helpers;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/6/14 20:58
 */
class TcpServerSock extends ServerSock implements Selectable {
	private final ServerSocketChannel ssc;
	private SelectorLoop loop;

	private AtomicInteger maxActiveConnection;

	TcpServerSock() throws IOException {
		ch$ = ssc = ServerSocketChannel.open();
		ssc.configureBlocking(false);
	}

	@Override
	public <T> ServerSock setOption(SocketOption<T> k, T v) throws IOException {
		if (k == TCP_MAX_ALIVE_CONNECTION) maxActiveConnection = new AtomicInteger((Integer) v);
		else return super.setOption(k, v);
		return this;
	}

	@Override
	public ServerSock bind(SocketAddress addr, int tcpMaxPending) throws IOException {
		ssc.bind(addr, tcpMaxPending);
		return this;
	}

	@Override
	public void register(SelectorLoop loop, Consumer<MyChannel> listener) throws IOException {
		if (listen$ != null && key.isValid()) throw new IllegalStateException("Already registered");

		this.loop = loop;
		listen$ = listener;

		try {
			loop.register(this, null, SelectionKey.OP_ACCEPT);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		if (key != null && ssc.keyFor(sel) != key) key.cancel();
		key = ssc.register(sel, ops, att);
	}

	@Override
	public void selected(int readyOps) throws Exception {
		try {
			if (maxActiveConnection == null) acceptLimitless();
			else acceptLimited();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void acceptLimitless() throws IOException {
		while (true) {
			SocketChannel sc = ssc.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			MyChannel ch = new TcpChImpl(sc, rcvBuf);

			listen$.accept(ch);
			if (ch.isOpen()) {
				ch.open();
				loop.register(ch, null);
			}
		}
	}
	private void acceptLimited() throws IOException {
		while (true) {
			if (maxActiveConnection.getAndDecrement() <= 0) {
				key.interestOps(0);
				maxActiveConnection.getAndIncrement();
				return;
			}

			SocketChannel sc = ssc.accept();
			if (sc == null) return;
			sc.configureBlocking(false);

			MyChannel ch = new TcpChImpl(sc, rcvBuf) {
				@Override
				protected void closeHandler() throws IOException {
					super.closeHandler();

					maxActiveConnection.getAndIncrement();
					SelectionKey key1 = TcpServerSock.this.key;
					if (key1.isValid() && key1.interestOps() == 0) key1.interestOps(SelectionKey.OP_ACCEPT);
				}
			};

			listen$.accept(ch);
			if (ch.isOpen()) {
				ch.open();
				loop.register(ch, null);
			}
		}
	}
}
