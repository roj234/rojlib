package ilib.net.mock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;

/**
 * @author solo6975
 * @since 2022/4/6 22:31
 */
public class ILOutboundMocker extends ChannelOutboundHandlerAdapter {
	private final PacketAdapter pa;

	public ILOutboundMocker(PacketAdapter pa) {
		this.pa = pa;
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise p) throws Exception {
		ByteBuf buf = null;

		try {
			buf = transform(ctx, (ByteBuf) msg);
			if (buf != null) {
				if (buf.isReadable()) {
					ctx.write(buf, p);
				} else {
					buf.release();
					ctx.write(Unpooled.EMPTY_BUFFER, p);
				}

				buf = null;
			} else {
				ctx.write(msg, p);
			}
		} catch (DecoderException e) {
			throw e;
		} catch (Throwable e) {
			throw new DecoderException(e);
		} finally {
			if (buf != null) {
				buf.release();
			}
		}
	}

	private ByteBuf transform(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		msg.markReaderIndex();
		ByteBuf buf = pa.processOutboundPacket(ctx, msg);
		msg.resetReaderIndex();
		return buf;
	}
}
