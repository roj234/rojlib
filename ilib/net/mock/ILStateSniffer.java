package ilib.net.mock;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;

/**
 * @author solo6975
 * @since 2022/4/7 0:05
 */
public class ILStateSniffer extends ChannelInboundHandlerAdapter {
	private final PacketAdapter pa;

	public ILStateSniffer(PacketAdapter pa) {
		this.pa = pa;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		EnumConnectionState state = EnumConnectionState.getFromPacket((Packet<?>) msg);
		pa.setConnectionState(state);
		ctx.fireChannelRead(msg);
	}
}
