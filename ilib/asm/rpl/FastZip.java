package ilib.asm.rpl;

import ilib.Config;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import net.minecraft.network.PacketBuffer;

import java.util.zip.Deflater;

/**
 * @author solo6975
 * @since 2022/4/6 22:41
 */
public class FastZip extends MessageToByteEncoder<ByteBuf> {
	private final byte[] tmp = new byte[1024];

	private final Deflater def;
	private int threshold;

	public FastZip(int thr) {
		System.err.println(thr);
		this.threshold = thr;
		this.def = new Deflater(Config.compressionLevel);
	}

	protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
		if (in.readableBytes() < threshold) {
			out.writeByte(0).writeBytes(in);
			return;
		}

		PacketBuffer pb = new PacketBuffer(out).writeVarInt(in.readableBytes());

		ByteBuf tmp1 = null;
		if (in.hasArray()) {
			def.setInput(in.array(), in.arrayOffset() + in.readerIndex(), in.readableBytes());
			in.skipBytes(in.readableBytes());
		} else {
			tmp1 = ctx.alloc().heapBuffer(Math.min(8192, in.readableBytes()));
		}

		try {
			do {
				while (true) {
					int i = def.deflate(tmp);
					if (i == 0) break;
					pb.writeBytes(tmp, 0, i);
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.readBytes(tmp1.array(), tmp1.arrayOffset(), cnt);
				def.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			def.finish();
			while (!def.finished()) {
				int i = def.deflate(tmp);
				if (i == 0) break;
				pb.writeBytes(tmp, 0, i);
			}
		} finally {
			if (tmp1 != null) while (tmp1.refCnt() > 0) tmp1.release();
			def.reset();
		}
	}

	public void setCompressionThreshold(int thresholdIn) {
		this.threshold = thresholdIn;
	}
}
