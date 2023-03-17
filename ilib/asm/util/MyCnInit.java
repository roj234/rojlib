package ilib.asm.util;

import ilib.Config;
import ilib.net.mock.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;

import net.minecraft.network.*;

/**
 * @author Roj234
 * @since 2020/9/6 1:46
 */
public class MyCnInit extends ChannelInitializer<Channel> {
	private final NetworkManager man;

	public MyCnInit(NetworkManager man) {
		this.man = man;
	}

	protected void initChannel(Channel channel) {
		try {
			channel.config().setOption(ChannelOption.TCP_NODELAY, true);
		} catch (ChannelException ignored) {}

		PacketAdapter pa = MockingUtil.getPacketAdapter();
		if (pa != null) {
			channel.pipeline()
				   .addLast("timeout", new ReadTimeoutHandler(Config.clientNetworkTimeout))
				   .addLast("splitter", new NettyVarint21FrameDecoder())
				   .addLast("decoder", new ILInboundMocker(pa))
				   .addLast("real_decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
				   .addLast("prepender", new NettyVarint21FrameEncoder())
				   .addLast("encoder", new ILOutboundMocker(pa))
				   .addLast("real_encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
				   .addLast("packet_handler", new ILStateSniffer(pa))
				   .addLast("real_packet_handler", man);
		} else {
			channel.pipeline()
				   .addLast("timeout", new ReadTimeoutHandler(Config.clientNetworkTimeout))
				   .addLast("splitter", new NettyVarint21FrameDecoder())
				   .addLast("decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
				   .addLast("prepender", new NettyVarint21FrameEncoder())
				   .addLast("encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
				   .addLast("packet_handler", man);
		}
	}
}
