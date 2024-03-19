package roj.net.ch;

import roj.io.NIOUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
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

	@Override
	public MyChannel udpCh() { return udp; }

	public final <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		if (k == StandardSocketOptions.SO_REUSEPORT) NIOUtil.setReusePort(uc, (boolean) v);
		else udp.setOption(k, v); return this; }
	public final <T> T option(SocketOption<T> k) throws IOException { return udp.getOption(k); }

	public ServerLaunch bind(SocketAddress a, int backlog) throws IOException { uc.bind(a); return this; }
	public SocketAddress localAddress() throws IOException { return uc.getLocalAddress(); }

	@Override
	public final ServerLaunch launch() throws IOException {
		udp.state = MyChannel.CONNECTED;

		if (initializator == null) {
			if (udp.pipelineHead == null) throw new IllegalStateException("no initializator");
		} else initializator.accept(udp);

		udp.open();
		loop().register(udp, null, SelectionKey.OP_READ);
		return this;
	}

	public final boolean isOpen() { return udp.isOpen(); }
	public final void close() throws IOException { udp.close(); }
}