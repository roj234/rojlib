package roj.plugin;

import roj.http.ws.WebSocketHandler;
import roj.net.ChannelCtx;
import roj.text.DateParser;
import roj.text.UTF8;
import roj.text.logging.Logger;
import roj.ui.ITerminal;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.awt.event.KeyEvent;
import java.io.IOException;

import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/21 0021 7:32
 */
final class WebTerminal extends WebSocketHandler implements ITerminal {
	public static final ThreadLocal<WebTerminal> ACTIVE = new ThreadLocal<>();
	static long timeout;
	static {
		Panger.CMD.register(literal("disconnect").executes(ctx -> {
			var wt = ACTIVE.get();
			if (wt == null) Terminal.warning("该指令只能在Web终端中执行");
			// 因为在当前线程上，所以有锁
			else wt.error(WebTerminal.ERR_CLOSED, "goodbye");
		}));
		Panger.CMD.onVirtualKey(key -> {
			if (key == (Terminal.VK_CTRL|KeyEvent.VK_Q)) {
				timeout = System.currentTimeMillis() + 300000;
				Terminal.error("Web终端功能关闭至"+DateParser.toLocalTimeString(timeout));
				return false;
			}
			if (key == (Terminal.VK_CTRL|KeyEvent.VK_C)) {
				if (ACTIVE.get() != null) {
					Terminal.warning("为了防止误触发, Ctrl+C已在Web终端中禁用, 请使用stop指令");
					return false;
				}
			}
			return null;
		});
	}

	private static final Logger LOGGER = Logger.getLogger();
	private final ByteList buffer = new ByteList();

	@Override public boolean readBack(boolean sync) {return false;}
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

		if (System.currentTimeMillis() < timeout) error(ERR_CLOSED, null);
	}

	@Override
	protected void onData(int ph, DynByteBuf in) {
		ACTIVE.set(this);
		try {
			Terminal.onInput(in, UTF8.CODER);
		} finally {
			ACTIVE.remove();
		}
		if (in.isReadable()) LOGGER.warn("{}接收到不完整的UTF-8字符", ch.remoteAddress());
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);
		Terminal.removeListener(this);
		LOGGER.warn("{}已断开", ctx.remoteAddress());
	}
}