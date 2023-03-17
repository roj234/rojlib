package roj.net.ch.handler;

import roj.config.MCfgParser;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 15:57
 */
public class BinaryJSON implements ChannelHandler {
	public static final BinaryJSON INSTANCE = new BinaryJSON();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.channelRead(MCfgParser.parse((DynByteBuf) msg));
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		ByteList tmp = IOUtil.getSharedByteBuf();
		((CEntry) msg).toBinary(tmp);

		DynByteBuf tmp1 = ctx.allocate(false, tmp.readableBytes()).put(tmp);
		try {
			ctx.channelWrite(tmp1);
		} finally {
			ctx.reserve(tmp1);
		}
	}
}
