package roj.net.ch.handler;

import roj.config.ParseException;
import roj.config.VinaryParser;
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
	private final VinaryParser parser = new VinaryParser();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		try {
			ctx.channelRead(parser.parseRaw((DynByteBuf) msg));
		} catch (ParseException ignored) {} // will not throw
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		ByteList tmp = IOUtil.getSharedByteBuf();
		parser.serialize(((CEntry) msg), tmp);
		ctx.channelWrite(tmp);
	}
}
