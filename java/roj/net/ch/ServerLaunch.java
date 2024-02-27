package roj.net.ch;

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
	public static final SelectorLoop DEFAULT_LOOPER = new SelectorLoop("NIO请求池", 0, 4, 60000, 127);

	public static final SocketOption<Integer> TCP_RECEIVE_BUFFER = new MyOption<>("TCP_RECEIVE_BUFFER", Integer.class);
	public static final SocketOption<Integer> TCP_MAX_CONNECTION = new MyOption<>("TCP_MAX_CONNECTION", Integer.class);

	SelectorLoop loop = DEFAULT_LOOPER;

	Consumer<MyChannel> initializator;

	ServerLaunch() {}

	public final boolean isTCP() { return getClass() != ServerLaunchUdp.class; }

	public MyChannel udpCh() { return null; }

	public static ServerLaunch tcp() throws IOException { return new ServerLaunchTcp(); }
	public static ServerLaunch udp() throws IOException { return new ServerLaunchUdp(); }
	public static ServerLaunch tcp(String name) throws IOException { return new ServerLaunchTcp(); }
	public static ServerLaunch udp(String name) throws IOException { return new ServerLaunchUdp(); }

	public abstract <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException;
	public abstract <T> T option(SocketOption<T> k) throws IOException;

	public final ServerLaunch loop(SelectorLoop s) { loop = s; return this; }
	public final SelectorLoop loop() { return loop == null ? DEFAULT_LOOPER : loop; }

	public abstract ServerLaunch bind(SocketAddress a, int backlog) throws IOException;
	public final ServerLaunch bind(SocketAddress a) throws IOException { return bind(a, 0); }
	public final ServerLaunch bind(int port) throws IOException { return bind(new InetSocketAddress(port)); }
	public final ServerLaunch bind(int port, int backlog) throws IOException { return bind(new InetSocketAddress(port), backlog); }
	public final ServerLaunch bind2(InetAddress addr, int port) throws IOException { return bind2(addr, port, 0); }
	public final ServerLaunch bind2(InetAddress addr, int port, int backlog) throws IOException { return bind(new InetSocketAddress(addr, port), backlog); }

	public abstract SocketAddress localAddress() throws IOException;

	public final ServerLaunch initializator(Consumer<MyChannel> i) {
		initializator = i;
		return this;
	}
	public final Consumer<MyChannel> initializator() { return initializator; }
	public abstract ServerLaunch launch() throws IOException;

	public abstract boolean isOpen();
	public abstract void close() throws IOException;
}