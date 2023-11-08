package roj.net.ch;

import roj.util.Helpers;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

/**
 * @author Roj233
 * @since 2022/10/11 20:03
 */
final class ServerLaunchUdp extends ServerLaunch {
	private final UdpChImpl udp;

	ServerLaunchUdp() throws IOException {
		DatagramChannel uc = DatagramChannel.open();
		uc.configureBlocking(false);
		udp = new UdpChImpl(uc, 0);
	}

	public final <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		if (k == CHANNEL_RECEIVE_BUFFER) udp.buffer = (Integer) v;
		else udp.setOption(k, v);
		return this;
	}
	public final <T> T option(SocketOption<T> k) throws IOException { return k == CHANNEL_RECEIVE_BUFFER ? Helpers.cast(udp.buffer) : udp.getOption(k); }

	@Override
	public final ServerLaunch launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("no initializator");

		initializator.accept(udp);
		udp.open();
		loop().register(udp, null, SelectionKey.OP_READ);
		return this;
	}

	public final boolean isOpen() { return udp.isOpen(); }
	public final void close() throws IOException { udp.close(); }
}
