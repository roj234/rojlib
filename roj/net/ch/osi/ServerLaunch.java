package roj.net.ch.osi;

import roj.concurrent.Shutdownable;
import roj.net.ch.MyChannel;
import roj.net.ch.SelectorLoop;
import roj.net.ch.ServerSock;

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
public final class ServerLaunch {
	private Shutdownable owner;
	private String threadPrefix = "Server Network I/O";
	private int threadInit = 0, threadMin = 1, threadMax = Runtime.getRuntime().availableProcessors()/2, threadTimeout = 60000, threadThreshold = 30;
	private boolean daemon, hasLoop;
	private SelectorLoop loop;

	private final ServerSock sock;
	private InetSocketAddress addr;
	private int backlog = 1000;

	private Consumer<MyChannel> initializator;

	private ServerLaunch(ServerSock sock) {
		this.sock = sock;
	}

	public static ServerLaunch tcp() throws IOException { return new ServerLaunch(ServerSock.openTCP()); }
	public static ServerLaunch udp() throws IOException { return new ServerLaunch(ServerSock.openUDP()); }

	public ServerLaunch owner(Shutdownable s) {
		if (loop != null) throw new IllegalStateException();
		this.owner = s;
		return this;
	}

	public ServerLaunch daemon(boolean s) {
		if (loop != null) throw new IllegalStateException();
		this.daemon = s;
		return this;
	}

	public ServerLaunch threadPrefix(String s) {
		if (loop != null) throw new IllegalStateException();
		this.threadPrefix = s;
		return this;
	}

	public ServerLaunch threadInit(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadInit = s;
		return this;
	}

	public ServerLaunch threadMin(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadMin = s;
		return this;
	}

	public ServerLaunch threadMax(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadMax = s;
		return this;
	}

	public ServerLaunch threadTimeout(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadTimeout = s;
		return this;
	}

	public ServerLaunch threadThreshold(int s) {
		if (loop != null) throw new IllegalStateException();
		this.threadThreshold = s;
		return this;
	}

	public ServerLaunch loop(SelectorLoop s) {
		if (loop != null) throw new IllegalStateException();
		this.loop = s;
		this.hasLoop = true;
		return this;
	}

	public <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		sock.setOption(k,v);
		return this;
	}

	public ServerLaunch listen(SocketAddress addr) {
		this.addr = (InetSocketAddress) addr;
		return this;
	}

	public ServerLaunch listen_(SocketAddress addr, int backlog) {
		this.addr = (InetSocketAddress) addr;
		this.backlog = backlog;
		return this;
	}

	public ServerLaunch listen(InetAddress addr, int port) {
		return listen(new InetSocketAddress(addr, port));
	}

	public ServerLaunch listen(InetAddress addr, int port, int backlog) {
		this.listen(addr, port);
		this.backlog = backlog;
		return this;
	}

	public ServerLaunch initializator(Consumer<MyChannel> i) {
		this.initializator = i;
		return this;
	}

	public SelectorLoop launch() throws IOException {
		if (initializator == null) throw new IllegalStateException("no initializator");

		sock.bind(addr, backlog);
		if (sock.isTCP()) {
			sock.register(getLoop(), sock -> {
				if (!sock.isOpen() && !hasLoop) {
					loop.shutdown();
					return;
				}

				try {
					MyChannel ctx = sock.accept();
					initializator.accept(ctx);
					ctx.open();
					loop.register(ctx, null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		} else {
			initializator.accept(sock.accept());
			sock.register(getLoop(), null);
		}

		return loop;
	}

	public SelectorLoop getLoop() {
		if (loop == null && !hasLoop) {
			loop = new SelectorLoop(owner, threadPrefix, threadInit, threadMin, threadMax, threadTimeout, threadThreshold, daemon);
		}
		return loop;
	}

	public InetSocketAddress address() {
		return addr;
	}
}
