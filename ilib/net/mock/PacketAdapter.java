package ilib.net.mock;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import net.minecraft.network.EnumConnectionState;

/**
 * @author solo6975
 * @since 2022/4/7 0:01
 */
public interface PacketAdapter {
	ByteBuf processInboundPacket(ChannelHandlerContext ctx, ByteBuf message) throws Exception;

	ByteBuf processOutboundPacket(ChannelHandlerContext ctx, ByteBuf message) throws Exception;

	void setConnectionState(EnumConnectionState state);
}
