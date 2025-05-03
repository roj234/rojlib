package roj.plugin;

import roj.http.WebSocketConnection;
import roj.net.ChannelCtx;
import roj.text.FastCharset;
import roj.text.logging.Logger;
import roj.ui.StdIO;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/21 7:32
 */
public class WebTerminal extends WebSocketConnection implements StdIO {
	private static final Logger LOGGER = Logger.getLogger();
	private final ByteList buffer = new ByteList();

	@Override public boolean unicode() {return true;}
	@Override public boolean read(boolean sync) {return sync;}
	@Override public void write(CharSequence str) {synchronized (buffer) {buffer.putUTFData(str);}}

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		super.handlerAdded(ctx);
		fragmentSize = Integer.MAX_VALUE;
		compressSize = 255;
		Terminal.addListener(this);
		LOGGER.warn("{}已连接", ctx.remoteAddress());
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		var ob = buffer;
		if (ob.isReadable() && !ctx.channel().isFlushing()) {
			synchronized (ob) {
				while (ob.readableBytes() > 4096) {
					send(ob.slice(4096));
					if (ctx.channel().isFlushing()) {ob.compact();return;}
				}
				send(ob);
				ob.clear();
			}
		}
	}

	@Override
	protected void onData(int ph, DynByteBuf in) {
		Terminal.onInput(in, FastCharset.UTF8());
		if (in.isReadable()) LOGGER.warn("{}接收到不完整的UTF-8字符", ch.remoteAddress());
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		Terminal.removeListener(this);
		LOGGER.warn("{}已断开", ctx.remoteAddress());
	}
}