package roj.net.ch;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

/**
 * @author Roj233
 * @since 2022/10/11 20:03
 */
final class ServerLaunchUdp extends ServerLaunch {
	private final DatagramChannel uc;
	private final UdpChImpl udp;

	ServerLaunchUdp() throws IOException {
		uc = DatagramChannel.open();
		udp = new UdpChImpl(uc);
	}

	public final <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException { udp.setOption(k, v); return this; }
	public final <T> T option(SocketOption<T> k) throws IOException { return udp.getOption(k); }

	@Override
	public final ServerLaunch launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("no initializator");

		uc.bind(addr);
		udp.state = MyChannel.CONNECTED;
		initializator.accept(udp);
		udp.open();
		loop().register(udp, null, SelectionKey.OP_READ);
		return this;
	}

	public final boolean isOpen() { return udp.isOpen(); }
	public final void close() throws IOException { udp.close(); }
}