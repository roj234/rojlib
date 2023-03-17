package ilib.asm.rpl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/6 22:12
 */
public class VarIntDecoder extends ByteToMessageDecoder {
	public VarIntDecoder() {
		// will change anything?
		setCumulator(COMPOSITE_CUMULATOR);
	}

	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> msg) throws Exception {
		in.markReaderIndex();

		int varInt = 0;
		int i = 0;

		byte b1;
		do {
			b1 = in.readByte();
			varInt |= (b1 & 0x7F) << (i++ * 7);
			if (i > 3) {
				throw new CorruptedFrameException("length wider than 21-bit");
			}

			if (!in.isReadable()) {
				in.resetReaderIndex();
				return;
			}
		} while ((b1 & 0x80) != 0);

		if (in.readableBytes() >= varInt) {
			msg.add(in.readBytes(varInt));
		} else {
			in.resetReaderIndex();
		}
	}
}
