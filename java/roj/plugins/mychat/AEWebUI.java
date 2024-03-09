package roj.plugins.mychat;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.net.ch.ChannelCtx;
import roj.net.http.srv.Request;
import roj.net.http.srv.ResponseHeader;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/7 0007 15:03
 */
public class AEWebUI extends WebsocketHandler {
	public static void fn() {

		WebsocketManager man = new WebsocketManager() {
			@Override
			protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
				return new AEWebUI();
			}
		};
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		ctx.channelOpened();
	}

	@Override
	protected void onData(int ph, DynByteBuf in) throws IOException {
		CMapping map;
		try {
			map = new JSONParser().parseRaw(in).asMap();
		} catch (ParseException e) {
			error(ERR_INVALID_DATA, e.getMessage());
			return;
		}
		if (map.containsKey("user")) {
			String userHash = map.getString("user");

			switch (map.getString("op")) {
				case "kick":
				case "speed_limit":
				case "flow_limit":
				case "time_limit":
				case "blacklist":
				case "whitelist":
			}
		} else {
			// GLOBAL operation
			switch (map.getString("op")) {
				case "reset":
				case "shutdown":
				case "motd":
				case "refresh_room":
				case "refresh_user":
			}
		}
	}
}