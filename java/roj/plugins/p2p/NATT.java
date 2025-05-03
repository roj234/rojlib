package roj.plugins.p2p;

import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.crypt.Base64;
import roj.http.HttpRequest;
import roj.io.IOUtil;
import roj.net.*;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * NAT Traversal
 * @author Roj234
 * @since 2024/1/10 4:42
 */
public final class NATT implements Closeable, ChannelHandler, Consumer<MyChannel> {
	public static void main(String[] args) throws Exception {
		SimpleList<NetworkInterface> interfaces = Net.getNetworkInterfaces();
		if (interfaces.isEmpty()) {
			System.out.println("No network interface available, abort");
			return;
		}

		NetworkInterface itf = interfaces.get(0);
		System.out.println("Choose "+itf);

		InetAddress localAddress = null;
		for (InterfaceAddress ia : itf.getInterfaceAddresses()) {
			InetAddress address = ia.getAddress();
			if (address instanceof Inet4Address) {
				localAddress = address;
				break;
			}
		}

		System.out.println("请选择作为发起方(o)或接受方(x)");
		char c = Terminal.readChar("ox");
		if (c == 0) return;
		InetSocketAddress remote;
		if (c == 'x') {
			String addr = Terminal.readString("请输入对方地址: ");
			remote = Net.parseAddress(addr, null);
		} else remote = null;

		long t = System.currentTimeMillis();
		System.out.println("Checking NAT type, this may cost upto 30 seconds");
		NATT natt = createTCP(new InetSocketAddress(localAddress, 0), remote, ch -> {
			ch.addLast("testA", new ChannelHandler() {
				@Override
				public void channelOpened(ChannelCtx ctx) throws IOException {
					if (remote != null) {
						DatagramPkt pkt = new DatagramPkt(remote, new ByteList().putAscii("testInfo12312"));
						System.out.println("channelOpened() called");
						ctx.channelWrite(pkt);
					}
				}

				@Override
				public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
					DatagramPkt pkt = (DatagramPkt) msg;
					System.out.println("接受: "+pkt.addr+":"+pkt.port+":"+pkt.buf);
				}
			});
		});
		System.out.println("init cost "+(System.currentTimeMillis()-t)+"ms");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				System.out.println("shutdown");
				natt.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}));

		int result = natt.init();
		switch (result) {
			case -1:
				System.out.println("网络状态较差,无法从该侧发起连接:"+natt);
			break;
			case 1:
				System.out.println("网络状态良好,可以直接连接该侧(可退出另一侧程序):"+natt);
			break;
			case 0:
				if (remote == null) {
					System.out.println("网络状态一般:"+natt);
					System.out.println("将该地址给对方:"+natt.remoteAddress.toString().substring(1));
					Terminal.pause();

					String addr = Terminal.readString("请输入对方的连接码: ");
					natt.connect(addr);

				} else {
					System.out.println("将连接码给对方:"+natt.connectCode());
					System.out.println("按回车键继续");
				}
			break;
		}

		while (true) {
			String s = Terminal.readString("输入数据或按x退出");
			if (s.equals("x")) System.exit(0);
			System.out.println("sending "+natt.localAddress +" => "+natt.peerAddress);
			((ServerLaunch) natt.keepalive).udpCh().fireChannelWrite(new DatagramPkt(natt.peerAddress, new ByteList().putAscii(s)));
		}
	}

	public static NATT createUDP(InetSocketAddress localAddr, @Nullable InetSocketAddress remoteAddr, Consumer<MyChannel> chInit) throws IOException {
		return new NATT(localAddr, remoteAddr, chInit, ServerLaunch.DEFAULT_LOOPER, Servers.getDefault(), 0);
	}
	public static NATT createTCP(InetSocketAddress localAddr, @Nullable InetSocketAddress remoteAddr, Consumer<MyChannel> chInit) throws IOException {
		return new NATT(localAddr, remoteAddr, chInit, ServerLaunch.DEFAULT_LOOPER, Servers.getDefault());
	}

	static final int TIMEOUT = 1500;

	public InetSocketAddress peerAddress;
	public final InetSocketAddress localAddress, remoteAddress;
	public final boolean tcp, upnp, master;
	public final String natType;

	private final InetSocketAddress keepaliveAddress;
	private final Closeable keepalive;
	private final SelectorLoop loop;
	private final Consumer<MyChannel> chInit;
	private Closeable server;

	private volatile boolean closed;

	private long timer;
	private int state;

	/**
	 * NAT类型检测参考
	 * <a href="https://www.rfc-editor.org/rfc/rfc5780">NAT Behavior Discovery Using Session Traversal Utilities for NAT (STUN)</a><br>
	 * <a href="https://i2.wp.com/img-blog.csdnimg.cn/20200408145057307.png">RFC3478 Binding Lifetime Discovery</a>
	 */
	// UDP
	public NATT(InetSocketAddress localAddr, @Nullable InetSocketAddress remoteAddr, Consumer<MyChannel> chInit, SelectorLoop loop, Servers servers, int ignored) throws IOException {
		if (!Net.getNetworkEndpoints().contains(localAddr.getAddress()))
			throw new IOException("必须选择一个endpoint而不是0.0.0.0作为地址");

		this.tcp = false;
		this.loop = loop;
		this.chInit = chInit;
		this.master = remoteAddr == null;
		this.peerAddress = remoteAddr;
		boolean ipv6 = localAddr.getAddress() instanceof Inet6Address;

		try {
			for (int i = 0; i < servers.stunServerCount; i++) {
				InetSocketAddress addr = servers.getStunServer(i, ipv6);
				if (addr == null) continue;
				STUN.Response r = STUN.request(addr, TIMEOUT, STUN.UDP, localAddr);
				if (r.errCode != 0) continue;

				this.remoteAddress = r.internetAddress;
				this.localAddress = localAddr = new InetSocketAddress(localAddr.getAddress(), r.localAddress.getPort());
				int port = localAddr.getPort();
				this.upnp = UPnPGateway.available()&&UPnPGateway.openPort("NATT", port, r.internetAddress.getPort(), false, 86400_000);

				boolean publicIp = Net.getNetworkEndpoints().contains(r.internetAddress.getAddress()) && r.internetAddress.getPort() == port;
				check: {
					for (i++; i < servers.stunServerCount; i++) {
						addr = servers.getStunServer(i, ipv6);
						if (addr == null) continue;
						STUN.Response r2 = STUN.request(addr, TIMEOUT, STUN.UDP, localAddr);
						if (r2.errCode == 0) {
							if (r2.internetAddress.getAddress().equals(r.internetAddress.getAddress())) {
								r2 = STUN.request(addr, TIMEOUT, STUN.UDP|STUN.CHANGE_PORT|STUN.CHANGE_IP, localAddr);

								InetSocketAddress sa = r2.serverAddress;
								if (sa != null && (sa.getAddress().equals(addr.getAddress()) || sa.getPort() == addr.getPort())) continue;

								if (publicIp) natType = r2.errCode == 0 ? "NAT_OPEN_INTERNET" : "NAT_SYMMETRIC_FIREWALL";
								else if (r2.errCode == 0) natType = "NAT_FULL_CONE";
								else natType = STUN.request(addr, TIMEOUT, STUN.UDP|STUN.CHANGE_PORT, localAddr).errCode == 0 ? "NAT_RESTRICTED" : "NAT_PORT_RESTRICTED";
							} else natType = "NAT_SYMMETRIC";

							break check;
						}
					}

					natType = "NAT_UNKNOWN";
				}

				ServerLaunch udp = ServerLaunch.udp().loop(loop).initializator(this).bind(localAddr);
				System.out.println("listening "+localAddr);
				keepalive = udp;

				if (remoteAddr == null) {
					if (natType.equals("NAT_OPEN_INTERNET")) {
						keepaliveAddress = null;
						udp.initializator(chInit);
					} else {
						keepaliveAddress = servers.getKeepaliveServer(false, ipv6);
					}
					udp.launch();
				} else {
					System.out.println("try establish connection from "+localAddr+" => "+remoteAddr);
					keepaliveAddress = null;
					for (int j = 0; j < 5; j++) {
						udp.udpCh().fireChannelWrite(new DatagramPkt(remoteAddr, new ByteList().putAscii("trashtrashtrashtrashtrashtrash")));
					}
				}

				return;
			}

			throw new IOException("no STUN servers available");
		} catch (Throwable e) {
			close();
			throw e;
		}
	}
	// TCP
	public NATT(InetSocketAddress localAddr, @Nullable InetSocketAddress remoteAddr, Consumer<MyChannel> chInit, SelectorLoop loop, Servers servers) throws IOException {
		if (!Net.getNetworkEndpoints().contains(localAddr.getAddress()))
			throw new IOException("必须选择一个endpoint而不是0.0.0.0作为地址");

		this.tcp = true;
		this.loop = loop;
		this.chInit = chInit;
		this.master = remoteAddr == null;
		boolean ipv6 = localAddr.getAddress() instanceof Inet6Address;

		try {
			if (master) {
				MyChannel ch = ClientLaunch.tcp().loop(loop).connect(servers.getKeepaliveServer(true, ipv6)).timeout(TIMEOUT).initializator(this)
										   .option(StandardSocketOptions.SO_REUSEADDR, true).option(StandardSocketOptions.SO_REUSEPORT, true).bind(localAddr).launch();

				keepaliveAddress = servers.getKeepaliveServer(true, ipv6);
				keepalive = ch;

				localAddr = (InetSocketAddress) ch.localAddress();
			} else {
				// send SYN
				SocketChannel keep = SocketChannel.open().setOption(StandardSocketOptions.TCP_NODELAY, true);
				Net.setReusePort(keep, true);

				keepaliveAddress = null;
				keepalive = keep.bind(localAddr);

				keep.configureBlocking(false);
				keep.connect(remoteAddr);
				localAddr = (InetSocketAddress) keep.getLocalAddress();
				System.out.println("try establish connection from "+localAddr+" => "+remoteAddr);
			}

			this.localAddress = localAddr;
			int port = localAddr.getPort();

			for (int i = servers.lastUdpOnly; i < servers.stunServerCount; i++) {
				InetSocketAddress addr = servers.getStunServer(i, ipv6);
				if (addr == null) continue;
				STUN.Response r = STUN.request(addr, TIMEOUT, 0, localAddr);
				if (r.errCode != 0) continue;

				remoteAddress = r.internetAddress;
				boolean publicIp = Net.getNetworkEndpoints().contains(remoteAddress.getAddress()) && remoteAddress.getPort() == port;
				this.upnp = UPnPGateway.available()&&UPnPGateway.openPort("NATT", port, remoteAddress.getPort(), tcp, 86400_000);

				boolean[] remoteConnected = new boolean[1];

				ServerLaunch sc = ServerLaunch.tcp();
				server = sc;
				sc.loop(loop).option(StandardSocketOptions.SO_REUSEADDR, true)
				  .option(StandardSocketOptions.SO_REUSEPORT, true).bind(localAddr).initializator(ch -> {
					  try {
						  ch.close();
					  } catch (IOException ignored) {}
					  remoteConnected[0] = true;
				  }).launch();

				Boolean isOpen = NATT.checkTCPPort(remoteAddress.getPort());

				if (chInit == null) sc.close();
				else sc.initializator(chInit);

				check:
				if (isOpen == null) natType = "NAT_UNKNOWN";
				else {
					assert remoteConnected[0] == isOpen;

					if (publicIp) natType = isOpen ? "NAT_OPEN_INTERNET" : "NAT_SYMMETRIC_FIREWALL";
					else if (isOpen) natType = "NAT_FULL_CONE";
					else {
						// RESTRICTED => 同一IP不同端口, 在localAddr使用checkTCPPort可解, 懒得做了

						int successCount = 0;
						for (i++; i < servers.stunServerCount; i++) {
							addr = servers.getStunServer(i, ipv6);
							if (addr == null) continue;
							STUN.Response r2 = STUN.request(addr, TIMEOUT, 0, localAddr);
							if (r2.errCode != 0) continue;

							if (!r2.internetAddress.getAddress().equals(remoteAddress.getAddress())) {
								natType = "NAT_SYMMETRIC";
								break check;
							}

							if (++successCount == 2) {
								natType = "NAT_PORT_RESTRICTED";
								break check;
							}
						}

						natType = "NAT_UNKNOWN";
					}
				}

				if (natType.equals("NAT_OPEN_INTERNET")) keepalive.close();
				return;
			}

			throw new IOException("no STUN server return ok");
		} catch (Throwable e) {
			close();
			throw e;
		}
	}

	public static Boolean checkTCPPort(int port) {
		try {
			ByteList data = HttpRequest.builder().url("http://portcheck.transmissionbt.com/"+port).execute(10000).bytes();
			char isOk = data.charAt(0);
			if (isOk == '1') return true;
			else if (isOk == '0') return false;
		} catch (Exception ignored) {}
		return null;
	}

	// region cone keepalive
	private void keepalive(ChannelCtx ctx, long time) throws IOException {
		if (tcp) {
			ctx.channelWrite(IOUtil.getSharedByteBuf().putAscii(
				"GET /~ HTTP/1.1\r\n"+
				"Host: "+keepaliveAddress.getHostString()+"\r\n"+
				"User-Agent: curl/8.0.0\r\n"+
				"Accept: */*\r\n"+
				"Connection: keep-alive\r\n"+
				"\r\n"));
		} else {
			int i = new Random().nextInt();
			String dnsRequest = "\1\0\0\1\0\0\0\0\0\0\3www\5baidu\3com\0\0\1\0\1";
			ctx.channelWrite(new DatagramPkt(keepaliveAddress, IOUtil.getSharedByteBuf().putShort(i).putAscii(dnsRequest)));
		}

		timer = time + 10000;
		state = 1;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException { keepalive(ctx, System.currentTimeMillis()); }
	@Override
	public void channelRead(ChannelCtx ctx, Object msg) { state = 2; timer = System.currentTimeMillis() + 30000; }
	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException { close(); }

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		if (state == 0) return;

		long time = System.currentTimeMillis();
		if (time > timer) {
			if (state == 1) ctx.close(); // no reply
			else keepalive(ctx, time);
		}
	}

	@Override
	public void accept(MyChannel ch) { ch.addLast("keepalive", this); }
	// endregion

	private final class UdpAdapter implements ChannelHandler, Function<Object, Boolean> {
		volatile EmbeddedChannel target;
		ChannelCtx ctx;

		@Override
		public void handlerAdded(ChannelCtx ctx) { this.ctx = ctx; }

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DatagramPkt pkt = (DatagramPkt) msg;
			// not check port
			if (pkt.addr.equals(keepaliveAddress.getAddress())) {
				ctx.channelRead(pkt);
			} else if (target != null) {
				target.fireChannelRead(pkt);
			}
		}

		@Override
		public Boolean apply(Object o) {
			try {
				ctx.channelWrite(o);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
			return true;
		}
	}

	public int init() throws IOException {
		assert chInit != null;

		return switch (natType) {
			default -> 0; // punch
			case "NAT_UNKNOWN", "NAT_SYMMETRIC", "NAT_SYMMETRIC_FIREWALL" -> -1; // unavailable
			case "NAT_OPEN_INTERNET", "NAT_FULL_CONE" -> {
				if (!tcp) {
					UdpAdapter pipe = new UdpAdapter();
					((ServerLaunch) keepalive).udpCh().addFirst("address_filter", pipe);

					EmbeddedChannel channel = EmbeddedChannel.createWritable(pipe);
					channel.setLocal(localAddress);
					chInit.accept(channel);

					pipe.target = channel;
				}
				// otherwise, already bound tcp initializator
				yield 1;
			}
		};
	}

	public boolean connect(String connectCode) throws IOException {
		if (!master) throw new IllegalStateException("not master");

		DynByteBuf buf = Base64.decode(connectCode, IOUtil.getSharedByteBuf());
		byte[] data = buf.readBytes(buf.readUnsignedByte());
		int port = buf.readUnsignedShort();
		String otherNat = buf.readUTF();
		if (natType.equals("NAT_PORT_RESTRICTED") && otherNat.startsWith("NAT_SYMMETRIC")) return false;

		InetSocketAddress addr = new InetSocketAddress(InetAddress.getByAddress(data), port);
		peerAddress = addr;
		System.out.println("try establish connection from "+localAddress+" => "+addr);
		if (tcp) {
			server = ClientLaunch.tcp().loop(loop).initializator(chInit).option(StandardSocketOptions.SO_REUSEADDR, true)
								 .option(StandardSocketOptions.SO_REUSEPORT, true).bind(localAddress).connect(addr).timeout(10000).launch()
								 .addFirst("close_handler", new ChannelHandler() {
									 @Override
									 public void channelClosed(ChannelCtx ctx) throws IOException { NATT.this.close(); }
								 });
		} else {
			ServerLaunch udp = (ServerLaunch) keepalive;
			MyChannel ch = udp.udpCh();
			ch.disconnect();
			ch.removeAll();
			for (int i = 0; i < 5; i++) {
				ch.fireChannelWrite(new DatagramPkt(addr, new ByteList().putAscii("tetetetetetetst")));
			}
			udp.initializator(chInit).launch();
		}

		return true;
	}

	public String connectCode() {
		ByteList buf = IOUtil.getSharedByteBuf();
		byte[] data = remoteAddress.getAddress().getAddress();
		buf.put(data.length).put(data).putShort(remoteAddress.getPort()).putUTF(natType);
		return Base64.encode(buf, IOUtil.getSharedCharBuf()).toString();
	}

	@Override
	public String toString() { return localAddress+" => "+remoteAddress+" ("+(tcp?"TCP":"UDP")+" via "+natType+")"; }

	public boolean isClosed() { return closed; }
	@Override
	public void close() throws IOException {
		if (closed) return;
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		// best practice... maybe
		Throwable e = null;
		try {
			if (upnp) UPnPGateway.closePort(localAddress.getPort(), tcp);
		} catch (Throwable ex) {
			e = ex;
		}
		try {
			if (keepalive != null) keepalive.close();
		} catch (Throwable ex) {
			if (e == null) e = ex;
			else e.addSuppressed(ex);
		}
		try {
			if (server != null) server.close();
		} catch (Throwable ex) {
			if (e == null) e = ex;
			else e.addSuppressed(ex);
		}
		if (e != null) Helpers.athrow(e);
	}
}