package ilib.net.mock.adapter;

import ilib.asm.rpl.VarIntEncoder;
import ilib.net.mock.MockingUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import roj.util.ByteList;

import net.minecraft.network.PacketBuffer;

/**
 * @author solo6975
 * @since 2022/4/7 11:39
 */
public class T_IdRemap implements Target {
	public final int fromId, toId, externalByte;

	public T_IdRemap(int fromId, int toId) {
		this.fromId = fromId;
		this.toId = toId;
		this.externalByte = VarIntEncoder.getVarIntLength(toId) - VarIntEncoder.getVarIntLength(fromId);
	}

	@Override
	public ByteBuf apply(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		if (externalByte == 0) {
			new PacketBuffer(msg).writeVarInt(toId);
			return null;
		} else {
			MockingUtil.readVarInt(msg);

			ByteBuf buf = ctx.alloc().heapBuffer(msg.writerIndex() + externalByte);
			new PacketBuffer(buf).writeVarInt(toId).writeBytes(msg);

			msg.release();
			return buf;
		}
	}

	@Override
	public void serialize(ByteList w) {
		w.put(ID_REMAP).putVarInt(fromId, false).putVarInt(toId, false);
	}

	@Override
	public String toString() {
		return "映射{" + fromId + "=>" + toId + ", db=" + externalByte + '}';
	}
}
