package ilib.asm.rpl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;

import net.minecraft.network.PacketBuffer;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * @author solo6975
 * @since 2022/4/6 22:41
 */
public class FastUnzip extends ChannelInboundHandlerAdapter {
	private final Inflater inf;
	private int threshold;
	private final byte[] tmp = new byte[1024];

	public FastUnzip(int thr) {
		System.err.println(thr);
		this.threshold = thr;
		this.inf = new Inflater();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf in = (ByteBuf) msg;
		if (in.readableBytes() == 0) return;

		PacketBuffer pb = new PacketBuffer(in);
		int len = pb.readVarInt();
		if (len == 0) {
			ctx.fireChannelRead(pb.readBytes(pb.readableBytes()));
		} else {
			if (len < threshold) {
				throw new DecoderException("Badly compressed packet - size of " + len + " is below server threshold of " + threshold);
			}

			if (len > 2097152) {
				throw new DecoderException("Badly compressed packet - size of " + len + " is larger than protocol maximum of " + 2097152);
			}

			ByteBuf out = ctx.alloc().heapBuffer(len);
			if (pb.hasArray()) {
				inf.setInput(pb.array(), pb.arrayOffset(), pb.readableBytes());
				pb.skipBytes(pb.readableBytes());
				pb = null;
			}

			byte[] tmp = this.tmp;

			try {
				do {
					int v = 0;
					while (!inf.needsInput()) {
						try {
							int j = inf.inflate(out.array(), out.arrayOffset() + v, len - v);
							if (j == 0) break;
							v += j;
						} catch (DataFormatException e) {
							throw new ZipException(e.getMessage());
						}
					}

					if (pb == null) {
						out.writerIndex(v);
						break;
					}

					if (!pb.isReadable()) {
						pb = null;
						continue;
					}

					int cnt = Math.min(pb.readableBytes(), tmp.length);
					pb.readBytes(tmp, 0, cnt);
					inf.setInput(tmp, 0, cnt);
				} while (true);

				//if (out.isWritable()) {
				//    throw new DecoderException("无效的压缩流(Too short)");
				//}

				ctx.fireChannelRead(out);
			} finally {
				inf.reset();
				while (out.refCnt() > 0) out.release();
			}
		}
	}

	public void setCompressionThreshold(int thresholdIn) {
		this.threshold = thresholdIn;
	}
}
