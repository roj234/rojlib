package roj.net;

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
	private final String name;
	private final DatagramChannel uc;
	private final UdpChImpl udp;

	ServerLaunchUdp(String name) throws IOException {
		this.name = name;
		uc = DatagramChannel.open();
		udp = new UdpChImpl(uc);
	}

	@Override
	public MyChannel udpCh() { return udp; }

	public final <T> ServerLaunch option(SocketOption<T> k, T v) throws IOException {
		if (k == StandardSocketOptions.SO_REUSEPORT) Net.setReusePort(uc, (boolean) v);
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
		if (name != null) SHARED.put(name, this);
		return this;
	}

	public final boolean isOpen() { return udp.isOpen(); }
	public final void close() throws IOException {
		if (name != null) SHARED.remove(name, this); udp.close(); }

	@Override
	public void addTCPConnection(MyChannel channel) throws IOException {
		throw new UnsupportedOperationException("未实现！");
		//TODO 实际上就是放一个UDP over TCP转发器，值得注意的是，launch不需要调用也能使用它，毕竟所有资源都是挂在对应channel上的
		// 如果是UDP数据包那么还要修改源地址
		// also, 需要在udp频道上也挂一个带timeout的UDP转发器和TCP转发器，把它们转发到对应频道上
		/*channel.addLast("udp转发器", new ChannelHandler() {
			@Override
			public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
				ChannelHandler.super.channelWrite(ctx, msg);
			}

			@Override
			public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
				ChannelHandler.super.channelRead(ctx, msg);
			}
		});
		channel.fireOpen();
		channel.readActive();*/
	}
}