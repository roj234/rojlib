package roj.plugins.http.template;

import roj.net.http.server.Response;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;

import java.io.File;

/**
 * @author Roj234
 * @since 2024/7/14 0014 11:08
 */
@SimplePlugin(id = "lsp")
public class LavaScript extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		var engine = new MyTemplateEngine();
		registerRoute("tpl", (req, rh) -> {
			String path = req.path();
			if (path.endsWith(".html")) {
				File tpl = new File(getDataFolder(), path);
				if (tpl.isFile()) return engine.render(tpl, req, rh);
			}

			rh.code(404);
			return Response.EMPTY;
		});
	}
}