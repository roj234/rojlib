package roj.net.http.server;

import roj.io.IOUtil;
import roj.net.http.HttpUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/22 23:18
 */
public class PathRouter implements Router {
	public final String path;
	public PathRouter(String path) {this.path = new File(path).getAbsolutePath();}
	public PathRouter(File path) {this.path = path.getAbsolutePath();}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path();
		var file = IOUtil.safePath(path, url);
		if (file == null) {
			rh.code(500);
			HttpServer11.LOGGER.fatal("啊呀呀，你写的路径过滤被绕过了！路径是："+url);
			return Response.internalError("啊呀呀，你写的路径过滤被绕过了！路径是："+url);
		}

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

		rh.code(200).header("cache-control", HttpUtil.CACHED_REVALIDATE);
		return Response.file(req, new DiskFileInfo(file));
	}
}