package roj.net.ch;

import roj.net.NetworkUtil;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/11 0011 18:16
 */
public abstract class ServerLaunch implements Closeable {
	public static final SelectorLoop DEFAULT_LOOPER = new SelectorLoop(null, "NIO请求池", 0, 4, 60000, 127);

	public static final SocketOption<Integer> CHANNEL_RECEIVE_BUFFER = new MyOption<>("CHANNEL_RECEIVE_BUFFER", Integer.class);
	public static final SocketOption<Integer> TCP_MAX_ALIVE_CONNECTION = new MyOption<>("TCP_MAX_ALIVE_CONNECTION", Integer.class);

	SelectorLoop loop = DEFAULT_LOOPER;
	InetSocketAddress addr;
	int backlog = 1000;

	Consumer<MyChannel> initializator;

	ServerLaunch() {}

	public final boolean isTCP() { return getClass() != ServerLaunchUdp.class; }

	public static ServerLaunch tcp() throws IOException { return new ServerLaunchTcp(); }
	public static ServerLaunch udp() throws IOException { return new ServerLaunchUdp(); }
	public static ServerLaunch tcp(String name) throws IOException { return new ServerLaunchTcp(); }
	public static ServerLaunch udp(String name) throws IOException { return new ServerLaunchUdp(); }

	public abstract <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException;
	public abstract <T> T option(SocketOption<T> k) throws IOException;

	public final ServerLaunch loop(SelectorLoop s) { loop = s; return this; }
	public final SelectorLoop loop() { return loop == null ? DEFAULT_LOOPER : loop; }

	public final ServerLaunch listen(SocketAddress a) { addr = (InetSocketAddress) a; return this; }
	public final ServerLaunch listen(SocketAddress a, int backlog) {
		addr = (InetSocketAddress) a;
		this.backlog = backlog;
		return this;
	}
	public final ServerLaunch listen(int port) {
		addr = new InetSocketAddress(NetworkUtil.anyLocalAddress(), port);
		return this;
	}
	public final ServerLaunch listen(int port, int backlog) {
		this.listen2(null, port);
		this.backlog = backlog;
		return this;
	}
	public final ServerLaunch listen2(InetAddress addr, int port) { return listen(new InetSocketAddress(addr, port)); }
	public final ServerLaunch listen2(InetAddress addr, int port, int backlog) {
		this.listen2(addr, port);
		this.backlog = backlog;
		return this;
	}
	public final SocketAddress localAddress() { return addr; }

	public ServerLaunch initializator(Consumer<MyChannel> i) {
		initializator = i;
		return this;
	}

	public abstract ServerLaunch launch() throws IOException;

	public abstract boolean isOpen();
	public abstract void close() throws IOException;
}
