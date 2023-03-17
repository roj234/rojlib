package ilib.asm.nx;

import ilib.asm.util.MyCnInit;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.LazyLoadBase;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class GroupNPE extends NetworkManager {
	public GroupNPE(EnumPacketDirection p_i46004_1_) {
		super(p_i46004_1_);
	}

	@Inject
	public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int port, boolean useEPollIO) {
		if (address instanceof Inet6Address) {
			System.setProperty("java.net.preferIPv4Stack", "false");
		}

		final NetworkManager man = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
		Class<? extends Channel> channelClass;
		LazyLoadBase<? extends EventLoopGroup> loader;
		if (Epoll.isAvailable() && useEPollIO) {
			channelClass = EpollSocketChannel.class;
			loader = CLIENT_EPOLL_EVENTLOOP;
		} else {
			channelClass = NioSocketChannel.class;
			loader = CLIENT_NIO_EVENTLOOP;
		}

		EventLoopGroup group;

		while (true) {
			if ((group = loader.getValue()) != null) break;

			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
		}

		new Bootstrap().group(group).handler(new MyCnInit(man)).channel(channelClass).connect(address, port).syncUninterruptibly();
		return man;
	}
}
