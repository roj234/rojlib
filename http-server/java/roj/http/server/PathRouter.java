package roj.http.server;

import roj.http.HttpUtil;
import roj.io.IOUtil;
import roj.text.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/7/22 23:18
 */
public class PathRouter implements Router, Predicate<String> {
	public final File path;
	public PathRouter(String path) {this.path = new File(path);}
	public PathRouter(File path) {this.path = path;}

	@Override
	public Content response(Request req, Response resp) throws IOException {
		String url = req.path();
		var file = IOUtil.resolveSafe(path, url);
		if (file == null) {
			resp.code(500);
			Logger.getLogger().fatal("啊呀呀，你写的路径过滤被绕过了！路径是："+url);
			return Content.internalError("啊呀呀，你写的路径过滤被绕过了！路径是："+url);
		}

		if (file.isDirectory()) {
			file = new File(file, "index.html");
			if (!file.isFile()) {
				resp.code(403);
				return Content.httpError(HttpUtil.FORBIDDEN);
			}
		} else if (!file.isFile()) {
			resp.code(404);
			return Content.httpError(HttpUtil.NOT_FOUND);
		}

		resp.code(200).setHeader("cache-control", HttpUtil.CACHED_REVALIDATE);
		return Content.file(req, new DiskFileInfo(file));
	}

	// (Optional) for OKRouter Prefix Delegation check
	@Override
	public boolean test(String url) {
		var file = IOUtil.resolveSafe(path, url);
		return file != null && file.exists();
	}
}