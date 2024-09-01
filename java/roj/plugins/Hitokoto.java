package roj.plugins;

import roj.config.ConfigMaster;
import roj.config.auto.Optional;
import roj.config.auto.SerializerFactory;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.ResponseHeader;
import roj.net.http.server.Router;
import roj.plugin.Panger;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;

import java.io.File;
import java.util.List;

/**
 * HTTP API for Hitokoto project
 * @see <a href="https://github.com/hitokoto-osc/">Hitokoto-OSC</a>
 * @author Roj234
 * @since 2024/8/30 0030 4:33
 */
@SimplePlugin(id = "hitokoto")
public class Hitokoto extends Plugin implements Router {
	@Optional
	public static class Item {
		String hitokoto, type, from, from_who, creator;
		int creator_uid;
		String commit_from;
		long created_at;
	}

	private List<Item> hitokoto;
	private int i;

	@Override
	protected void onEnable() throws Exception {
		try (var tr = TextReader.auto(new File(Panger.getInstance().getPluginFolder(), "Core/hitokoto.csv"))) {
			hitokoto = ConfigMaster.CSV.readObject(SerializerFactory.SAFE.listOf(Item.class), tr);
		}
		registerRoute("hitokoto", this, false);
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		String path = req.path();
		if (path.equals("")) return Response.json(ConfigMaster.JSON.writeObject(hitokoto.get(i++ % hitokoto.size()), new CharList()));
		else if (TextUtil.isNumber(path) == 0) return Response.json(ConfigMaster.JSON.writeObject(hitokoto.get(Integer.parseInt(path)), new CharList()));
		throw IllegalRequestException.NOT_FOUND;
	}
}
