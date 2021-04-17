package roj.net.ch.osi;

import roj.concurrent.Shutdownable;
import roj.net.ch.MyChannel;
import roj.net.ch.SelectorLoop;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/10/11 0011 18:16
 */
public final class ClientLaunch {
	private Shutdownable owner;
	private String threadPrefix = "Client Network I/O";
	private int threadInit = 0, threadMin = 0, threadMax = 1, threadTimeout = 10000, threadThreshold = 0;
	private SelectorLoop loop;
	private boolean daemon = true;

	private final MyChannel sock;
	private InetSocketAddress addr;
	private int connectTimeout;

	private Consumer<MyChannel> initializator;

	private ClientLaunch(MyChannel sock) {this.sock = sock;}

	public static ClientLaunch tcp() throws IOException { return new ClientLaunch(MyChannel.openTCP()); }
	public static ClientLaunch udp() throws IOException { return new ClientLaunch(MyChannel.openUDP()); }

	public ClientLaunch daemon(boolean s) {
		if (loop != null) throw new IllegalStateException();
		this.daemon = s;
		return this;
	}

	public ClientLaunch owner(Shutdownable s) {
		if (loop != null) throw new IllegalStateException();
		this.owner = s;
		return this;
	}

	public ClientLaunch threadPrefix(String s) {
		if (loop != null) throw new IllegalStateException();
		this.threadPrefix = s;
		return this;
	}

	public ClientLaunch threadInit(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadInit = s;
		return this;
	}

	public ClientLaunch threadMin(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadMin = s;
		return this;
	}

	public ClientLaunch threadMax(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadMax = s;
		return this;
	}

	public ClientLaunch threadTimeout(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadTimeout = s;
		return this;
	}

	public ClientLaunch threadThreshold(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadThreshold = s;
		return this;
	}

	public ClientLaunch loop(SelectorLoop s) {
		if (loop != null) throw new IllegalStateException();
		this.loop = s;
		return this;
	}

	public <T> ClientLaunch option(SocketOption<T> k, T v) throws IOException {
		sock.setOption(k,v);
		return this;
	}

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

	public ClientLaunch timeout(int timeout) {
		this.connectTimeout = timeout;
		return this;
	}

	public ClientLaunch initializator(Consumer<MyChannel> i) {
		this.initializator = i;
		return this;
	}

	public SelectorLoop launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("initializator == null");

		boolean selfLoop = loop == null;
		if (selfLoop) loop = new SelectorLoop(owner, threadPrefix, threadInit, threadMin, threadMax, threadTimeout, threadThreshold, daemon);

		initializator.accept(sock);

		if (sock.isTCP()) {
			if (addr == null) throw new BindException("No address specified");
			if (addr.getAddress() == null) throw new BindException("AnyLocalAddress() is not suitable for client!");
			sock.connect(addr, connectTimeout);
		} else {
			if (addr != null) {
				if (addr.getAddress() == null) throw new BindException("AnyLocalAddress() is not suitable for client!");
				sock.connect(addr, connectTimeout);
			} else {
				sock.open();
			}
		}

		loop.register(sock, selfLoop?(s)->loop.shutdown():null, SelectionKey.OP_CONNECT|SelectionKey.OP_READ);

		return loop;
	}
}
