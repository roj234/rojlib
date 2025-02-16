package roj.plugins.http.template;

import roj.config.data.CEntry;
import roj.http.HttpUtil;
import roj.http.server.DiskFileInfo;
import roj.http.server.Response;
import roj.plugin.Plugin;

import java.io.File;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/7/14 0014 11:08
 */
public class LavaScript extends Plugin {
	MyTemplateEngine engine = new MyTemplateEngine();

	@Override
	protected void onEnable() throws Exception {
		for (Map.Entry<String, CEntry> entry : getConfig().getMap("paths").entrySet()) {
			var basePath = new File(getDataFolder(), entry.getValue().asString());

			registerRoute(entry.getKey()+"/", (req, rh) -> {
				File file = new File(basePath, req.path());

				if (file.isDirectory()) {
					file = new File(file, "index.html");
					if (!file.isFile()) {
						rh.code(403);
						return Response.httpError(HttpUtil.FORBIDDEN);
					}
				} else if (!file.isFile()) {
					rh.code(404);
					return Response.httpError(HttpUtil.NOT_FOUND);
				}

				if (file.getName().endsWith(".html")) return engine.render(file, req, rh);

				rh.code(200).header("cache-control", HttpUtil.CACHED_REVALIDATE);
				return Response.file(req, new DiskFileInfo(file));
			});
			getLogger().warn("已注册路径路由器 {}/ => ", entry.getKey(), basePath);
		}
	}
}