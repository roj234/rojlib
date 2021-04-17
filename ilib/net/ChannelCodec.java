package ilib.net;

import ilib.ImpLib;
import io.netty.buffer.Unpooled;
import roj.collect.IntMap;
import roj.collect.ToIntMap;
import roj.util.Helpers;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;

import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Supplier;

/**
 * 消息编解码器
 */
final class ChannelCodec {
	private final IntMap<HandlerWrap<?>> indexes = new IntMap<>(4);
	private final ToIntMap<Class<? extends IMessage>> types = new ToIntMap<>(4);

	final Side side;
	final String channel;

	ChannelCodec(Side side, String channel) {
		this.side = side;
		this.channel = channel;
	}

	void addEnc(int id, Class<? extends IMessage> type) {
		this.types.putInt(type, id);
	}

	<M extends IMessage> void addDec(int id, Supplier<M> type, IMessageHandler<M> handler) {
		this.indexes.putInt(id, new HandlerWrap<>(handler, type));
	}

	final ProxyPacket encode(IMessage msg) {
		int id = this.types.getOrDefault(msg.getClass(), -1);
		if (id < 0) {
			throw new RuntimeException("Sending undefined packet " + msg.getClass().getName());
		}
		PacketBuffer pb = new PacketBuffer(Unpooled.buffer(64)).writeVarInt(id);
		msg.toBytes(pb);
		return new ProxyPacket(pb, channel);
	}

	@SuppressWarnings("unchecked")
	final void decode(ProxyPacket packet, INetHandler handler) {
		PacketBuffer pb = packet.payload();
		if (!pb.isReadable()) {
			ImpLib.logger().warn("Empty packet from " + handler);
			return;
		}

		int id = pb.readVarInt();
		HandlerWrap<?> clazz = this.indexes.get(id);
		if (clazz == null) {
			ImpLib.logger().warn("Unknown packet #" + id + " from " + handler);
			return;
		}
		IMessage msg = clazz.supplier.get();
		msg.fromBytes(pb);
		pb.release();

		IMessageHandler<IMessage> h = clazz.handler;
		if (h == null) h = (IMessageHandler<IMessage>) msg;

		h.onMessage(msg, new MessageContext(handler, this.side));
	}

	static class HandlerWrap<M extends IMessage> {
		final IMessageHandler<IMessage> handler;
		final Supplier<M> supplier;

		HandlerWrap(IMessageHandler<M> handler, Supplier<M> supplier) {
			this.handler = Helpers.cast(handler);
			this.supplier = supplier;
		}

		@Override
		public String toString() {
			return "HW{" + handler + ", " + supplier + '}';
		}
	}
}
