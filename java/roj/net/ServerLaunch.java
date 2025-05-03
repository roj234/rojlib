package roj.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/11 18:16
 */
public abstract class ServerLaunch implements Closeable {
	public static final SelectorLoop DEFAULT_LOOPER = new SelectorLoop("RojLib 网络", 0, 4, 60000, 127);

	public static final SocketOption<Integer> TCP_RECEIVE_BUFFER = new MyOption<>("TCP_RECEIVE_BUFFER", Integer.class);
	public static final SocketOption<Integer> TCP_MAX_CONNECTION = new MyOption<>("TCP_MAX_CONNECTION", Integer.class);
	public static final ConcurrentHashMap<String, ServerLaunch> SHARED = new ConcurrentHashMap<>();

	SelectorLoop loop = DEFAULT_LOOPER;

	Consumer<MyChannel> initializator;

	ServerLaunch() {}

	public final boolean isTCP() { return getClass() != ServerLaunchUdp.class; }

	public MyChannel udpCh() { return null; }

	public static ServerLaunch tcp() throws IOException {return new ServerLaunchTcp(null);}
	public static ServerLaunch udp() throws IOException {return new ServerLaunchUdp(null);}
	public static ServerLaunch tcp(String name) throws IOException {return new ServerLaunchTcp(name);}
	public static ServerLaunch udp(String name) throws IOException {return new ServerLaunchUdp(name);}
	public static ServerLaunch shadow(String name, Consumer<MyChannel> initializator) {return new Shadow(name).initializator(initializator);}
	private static final class Shadow extends ServerLaunch {
		private final String name;
		public Shadow(String name) {this.name = name;SHARED.put(name, this);}

		@Override public <T> ServerLaunch option(SocketOption<T> k, T v) {throw new UnsupportedOperationException();}
		@Override public <T> T option(SocketOption<T> k) {return null;}
		@Override public ServerLaunch bind(SocketAddress a, int backlog) {throw new UnsupportedOperationException();}
		@Override public SocketAddress localAddress() {return null;}
		@Override public ServerLaunch launch() {return this;}
		@Override public boolean isOpen() {return true;}
		@Override public void close() {SHARED.remove(name, this);}
		@Override public void addTCPConnection(MyChannel channel) throws IOException {
			initializator.accept(channel);
			channel.fireOpen();
			channel.readActive();
		}
	}

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

	public abstract void addTCPConnection(MyChannel channel) throws IOException;
}