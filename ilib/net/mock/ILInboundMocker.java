package ilib.net.mock;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.EncoderException;

/**
 * @author solo6975
 * @since 2022/4/6 22:31
 */
public class ILInboundMocker extends ChannelInboundHandlerAdapter {
	private final PacketAdapter pa;

	public ILInboundMocker(PacketAdapter pa) {
		this.pa = pa;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buf = null;

		try {
			buf = transform(ctx, (ByteBuf) msg);
			if (buf != null) {
				if (buf.isReadable()) {
					ctx.fireChannelRead(buf);
				}

				buf = null;
			} else {
				ctx.fireChannelRead(msg);
			}
		} catch (EncoderException e) {
			throw e;
		} catch (Throwable e) {
			throw new EncoderException(e);
		} finally {
			if (buf != null) {
				buf.release();
			}
		}
	}

	private ByteBuf transform(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		msg.markReaderIndex();
		ByteBuf buf = pa.processInboundPacket(ctx, msg);
		msg.resetReaderIndex();
		return buf;
	}
}
