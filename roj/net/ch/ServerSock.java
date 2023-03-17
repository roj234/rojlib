package roj.net.ch;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/6/14 20:23
 */
public abstract class ServerSock implements Closeable {
	public static final SocketOption<Integer> CHANNEL_RECEIVE_BUFFER = new MyOption<>("CHANNEL_RECEIVE_BUFFER", Integer.class);
	public static final SocketOption<Integer> TCP_MAX_ALIVE_CONNECTION = new MyOption<>("TCP_MAX_ALIVE_CONNECTION", Integer.class);

	NetworkChannel ch$;
	SelectionKey key;

	Consumer<MyChannel> listen$;
	int rcvBuf = 1536;

	ServerSock() {}

	public static ServerSock openTCP() throws IOException { return new TcpServerSock(); }
	public static ServerSock openUDP() throws IOException { return new UdpServerSock(); }

	public <T> ServerSock setOption(SocketOption<T> k, T v) throws IOException {
		if (k == CHANNEL_RECEIVE_BUFFER) rcvBuf = (Integer) v;
		else ch$.setOption(k, v);
		return this;
	}
	public final <T> T getOption(SocketOption<T> k) throws IOException { return ch$.getOption(k); }

	public final SocketAddress localAddress() throws IOException { return ch$.getLocalAddress(); }

	public ServerSock bind(SocketAddress addr, int tcpMaxPending) throws IOException { ch$.bind(addr); return this; }
	public ServerSock bind(InetAddress address, int port, int tcpMaxPending) throws IOException { return bind(new InetSocketAddress(address, port), tcpMaxPending); }

	public abstract void register(SelectorLoop loop, Consumer<MyChannel> listener) throws IOException;

	public boolean isOpen() { return ch$.isOpen(); }
	public void close() throws IOException { ch$.close(); }

	public final boolean isTCP() { return getClass() != UdpServerSock.class; }
}
