package roj.plugins.mychat;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.net.ChannelCtx;
import roj.net.http.ws.WebSocketHandler;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/7 0007 15:03
 */
public class AEWebUI extends WebSocketHandler {
	public static void fn() {
		//Response.websocket(req, request -> new AEWebUI());
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		ctx.channelOpened();
	}

	@Override
	protected void onData(int ph, DynByteBuf in) throws IOException {
		CMap map;
		try {
			map = new JSONParser().parse(in).asMap();
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