package ilib.net;

import ilib.ImpLib;
import org.apache.logging.log4j.Level;

import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2020/10/1 17:53
 */
@AddMinecraftPacket
public class ProxyPacket implements Packet<INetHandler> {
	private PacketBuffer payload;
	private String channel;

	public ProxyPacket() {}

	public ProxyPacket(PacketBuffer payload, String channel) {
		this.payload = payload;
		this.channel = channel;
	}

	@Override
	public void readPacketData(PacketBuffer buffer) {
		channel = buffer.readString(127);
		this.payload = new PacketBuffer(buffer.readSlice(buffer.readableBytes()));
	}

	@Override
	public void writePacketData(PacketBuffer buffer) {
		buffer.writeString(channel).writeBytes(payload);
	}

	@Override
	public void processPacket(@Nonnull final INetHandler handler) {
		MyChannel channel = MyChannel.CHANNELS.get(this.channel);
		if (channel != null) {
			try {
				if (handler instanceof INetHandlerPlayServer) {
					channel.serverCodec.decode(this, handler);
				} else {
					channel.clientCodec.decode(this, handler);
				}
			} catch (Throwable t) {
				ImpLib.logger().catching(Level.FATAL, new RuntimeException("There was a critical exception handling a packet on channel " + this.channel, t));
				MyChannel.kickWithMessage(handler, new TextComponentString("在数据包处理过程中发生了异常, 是的, ①个异常\n为了防止各种BUG发生, 您的连接已经中断"));
			}
		}
	}

	public PacketBuffer payload() {
		return this.payload;
	}

	@Override
	public String toString() {
		return "ProxyPacket{'" + channel + "'}";
	}
}
