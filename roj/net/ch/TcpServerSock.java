package roj.net.ch;

import roj.util.Helpers;

import java.io.IOException;
import java.nio.channels.*;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/6/14 20:58
 */
class TcpServerSock extends ServerSock implements Selectable {
	Consumer<ServerSock> listen$;
	SelectionKey key;
	ServerSocketChannel ssc;

	TcpServerSock() throws IOException {
		ch$ = ssc = ServerSocketChannel.open();
		ssc.configureBlocking(false);
	}

	@Override
	public ServerSock register(SelectorLoop loop, Consumer<ServerSock> listener) throws IOException {
		if (listen$ != null && key.isValid()) throw new IllegalStateException("Already registered");

		listen$ = listener;
		try {
			loop.register(this, Helpers.cast(listener), SelectionKey.OP_ACCEPT);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
		return this;
	}

	@Override
	public MyChannel accept() throws IOException {
		if (!ssc.isOpen()) throw new ClosedChannelException();

		SocketChannel sc = ssc.accept();
		sc.configureBlocking(false);

		return new TcpChImpl(sc, rcvBuf);
	}

	@Override
	public void selected(int readyOps) throws Exception {
		listen$.accept(this);
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		if (key != null && ssc.keyFor(sel) != key) key.cancel();
		key = ssc.register(sel, ops, att);
	}
}
