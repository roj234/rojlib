package roj.plugin;

import roj.config.serial.ToJson;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.ResponseHeader;
import roj.http.server.auto.GET;
import roj.http.server.auto.Interceptor;
import roj.net.ChannelCtx;
import roj.text.DateTime;
import roj.ui.Tty;
import roj.util.DynByteBuf;

import java.awt.event.KeyEvent;
import java.io.IOException;

import static roj.plugin.PanTweaker.CONFIG;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/23 1:40
 */
@Interceptor("PermissionManager")
final class WebUI {
	private final boolean terminal;
	public WebUI(boolean terminal) {this.terminal = terminal;}

	@GET
	public Content list(Request req) {
		var ser = new ToJson();
		ser.valueMap();
		ser.key("ok");
		ser.value(true);
		ser.key("data");
		ser.value(Jocker.motds.get(((int)System.nanoTime()&Integer.MAX_VALUE)% Jocker.motds.size()));
		ser.key("menus");
		CONFIG.getList("webui").accept(ser);
		return Content.json(ser.getValue());
	}

	@GET
	public void terminal(Request req, ResponseHeader rh) throws IOException {
		if (!terminal || System.currentTimeMillis() < XTerm.timeout) {
			rh.code(403).body(Content.internalError("该功能未启用"));
			return;
		}

		if (req.header("upgrade").equals("websocket")) {
			rh.body(Content.websocket(req, request -> new XTerm()));
		} else {
			rh.body(Content.text("200 OK"));
		}
	}
	private static final class XTerm extends WebTerminal {
		static final ThreadLocal<WebTerminal> ACTIVE = new ThreadLocal<>();

		static long timeout;
		static {
			Jocker.CMD.register(literal("disconnect").executes(ctx -> {
				var wt = ACTIVE.get();
				if (wt == null) Tty.warning("该指令只能在Web终端中执行");
				// 因为在当前线程上，所以有锁
				else wt.sendClose(ERR_CLOSED, "goodbye");
			}));
			Jocker.CMD.onVirtualKey(key -> {
				if (key == (Tty.VK_CTRL| KeyEvent.VK_Q)) {
					timeout = System.currentTimeMillis() + 300000;
					Tty.error("Web终端功能关闭至"+ DateTime.toLocalTimeString(timeout));
					return false;
				}
				if (key == (Tty.VK_CTRL|KeyEvent.VK_C)) {
					if (ACTIVE.get() != null) {
						Tty.warning("为了防止误触发, Ctrl+C已在Web终端中禁用, 请使用stop指令");
						return false;
					}
				}
				return null;
			});
		}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			super.channelTick(ctx);
			if (System.currentTimeMillis() < timeout) sendClose(ERR_CLOSED, "disabled");
		}

		@Override
		protected void onData(int frameType, DynByteBuf in) {
			ACTIVE.set(this);
			try {
				super.onData(frameType, in);
			} finally {
				ACTIVE.remove();
			}
		}
	}
}