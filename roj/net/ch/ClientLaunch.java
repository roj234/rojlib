package roj.net.ch;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

import static roj.net.ch.ServerLaunch.DEFAULT_LOOPER;

/**
 * @author Roj234
 * @since 2022/10/11 0011 18:16
 */
public final class ClientLaunch {
	private SelectorLoop loop = DEFAULT_LOOPER;

	private final MyChannel ch;
	private InetSocketAddress addr;
	private int connectTimeout = 2000;

	private Consumer<MyChannel> initializator;

	private ClientLaunch(MyChannel ch) { this.ch = ch; }

	public static ClientLaunch tcp() throws IOException { return new ClientLaunch(MyChannel.openTCP()); }
	public static ClientLaunch udp() throws IOException { return new ClientLaunch(MyChannel.openUDP()); }
	public static ClientLaunch tcp(String name) throws IOException { return new ClientLaunch(MyChannel.openTCP()); }
	public static ClientLaunch udp(String name) throws IOException { return new ClientLaunch(MyChannel.openUDP()); }

	public ClientLaunch daemon(boolean s) {
		if (loop != null) throw new IllegalStateException();
		return this;
	}

	public final ClientLaunch loop(SelectorLoop s) { loop = s; return this; }
	public final SelectorLoop loop() { return loop == null ? DEFAULT_LOOPER : loop; }

	public <T> ClientLaunch option(SocketOption<T> k, T v) throws IOException { ch.setOption(k,v); return this; }
	public <T> T option(SocketOption<T> k) throws IOException { return ch.getOption(k); }

	public ClientLaunch connect(URL url) {
		this.addr = new InetSocketAddress(url.getHost(), url.getPort()<0?url.getDefaultPort():url.getPort());
		return this;
	}
	public ClientLaunch connect(SocketAddress addr) {
		this.addr = (InetSocketAddress) addr;
		return this;
	}
	public ClientLaunch connect(InetAddress addr, int port) {
		this.addr = new InetSocketAddress(addr, port);
		return this;
	}
	public InetSocketAddress address() { return addr; }

	public ClientLaunch timeout(int x) { connectTimeout = x; return this; }

	public ClientLaunch initializator(Consumer<MyChannel> i) {
		initializator = i;
		return this;
	}

	public MyChannel channel() { return ch; }
	public MyChannel launch() throws IOException {
		if (initializator != null) initializator.accept(ch);
		if (channel().handlers().isEmpty()) throw new IllegalStateException("no handler added");

		if (ch.isTCP()) {
			if (addr == null) throw new BindException("No address specified");
			if (addr.getAddress() == null) throw new BindException("AnyLocalAddress() is not suitable for client!");
			ch.connect(addr, connectTimeout);
		} else {
			if (addr != null) {
				if (addr.getAddress() == null) throw new BindException("AnyLocalAddress() is not suitable for client!");
				ch.connect(addr, connectTimeout);
			} else {
				ch.open();
			}
		}

		loop().register(ch, null, SelectionKey.OP_CONNECT|SelectionKey.OP_READ);
		return ch;
	}
}
