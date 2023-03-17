package roj.net.ch;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/6/14 20:23
 */
public abstract class ServerSock implements Closeable {
	public static final SocketOption<Integer> CHANNEL_RECEIVE_BUFFER = new SocketOption<Integer>() {
		@Override
		public String name() {
			return "CHANNEL_RECEIVE_BUFFER";
		}

		@Override
		public Class<Integer> type() {
			return Integer.class;
		}
	};

	NetworkChannel ch$;
	int rcvBuf = 1536;

	ServerSock() {}

	public static ServerSock openTCP() throws IOException { return new TcpServerSock(); }
	public static ServerSock openUDP() throws IOException { return new UdpServerSock(); }

	public final <T> ServerSock setOption(SocketOption<T> k, T v) throws IOException {
		if (k == CHANNEL_RECEIVE_BUFFER) {
			rcvBuf = (Integer) v;
		} else {
			ch$.setOption(k, v);
		}
		return this;
	}
	public final <T> T getOption(SocketOption<T> k) throws IOException {
		return ch$.getOption(k);
	}

	public final SocketAddress localAddress() throws IOException {
		return ch$.getLocalAddress();
	}

	public ServerSock bind(SocketAddress addr, int backlog) throws IOException {
		ch$.bind(addr);
		return this;
	}
	public ServerSock bind(InetAddress address, int port, int backlog) throws IOException {
		return bind(new InetSocketAddress(address, port), backlog);
	}

	public abstract ServerSock register(SelectorLoop loop, Consumer<ServerSock> listener) throws IOException;
	public abstract MyChannel accept() throws IOException;

	public boolean isOpen() {
		return ch$.isOpen();
	}
	public void close() throws IOException {
		ch$.close();
	}

	public final boolean isTCP() {
		return getClass() != UdpServerSock.class;
	}
}
