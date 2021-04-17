package ilib.asm.rpl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import net.minecraft.network.PacketBuffer;

/**
 * @author solo6975
 * @since 2022/4/6 22:12
 */
@ChannelHandler.Sharable
public class VarIntEncoder extends MessageToByteEncoder<ByteBuf> {
	private static final int[] VARINT_LENGTH = new int[33];

	static {
		for (int i = 0; i <= 32; ++i) {
			VARINT_LENGTH[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
		}
		VARINT_LENGTH[32] = 1;
	}

	public static int getVarIntLength(int value) {
		return VARINT_LENGTH[Integer.numberOfLeadingZeros(value)];
	}

	public VarIntEncoder() {}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
		int r = in.readableBytes();
		int len = PacketBuffer.getVarIntSize(r);
		if (len > 3) {
			throw new IllegalArgumentException("Packet too long: " + r);
		} else {
			PacketBuffer pb = new PacketBuffer(out);
			pb.ensureWritable(len + r);
			pb.writeVarInt(r).writeBytes(in, in.readerIndex(), r);
		}
	}
}
