package roj.net.ch;

import roj.util.Helpers;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/10/11 20:03
 */
class UdpServerSock extends ServerSock {
	private final UdpChImpl udp;

	UdpServerSock() throws IOException {
		DatagramChannel uc = DatagramChannel.open();
		uc.configureBlocking(false);
		ch$ = uc;
		udp = new UdpChImpl(uc, 0);
	}

	@Override
	public MyChannel accept() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public ServerSock register(SelectorLoop loop, Consumer<ServerSock> listener) throws IOException {
		if (listener != null) throw new IllegalStateException("UDP listener is not supported");

		udp.buffer = rcvBuf;
		udp.open();

		try {
			loop.register(udp, null, SelectionKey.OP_READ);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
		return this;
	}
}
