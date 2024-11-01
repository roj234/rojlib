package roj.plugins.http.php;

import org.jetbrains.annotations.Nullable;
import roj.concurrent.TaskExecutor;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.config.data.Type;
import roj.io.IOUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.*;
import roj.plugin.PanHttp;
import roj.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/7/10 0010 3:17
 */
public class PHPPlugin extends Plugin implements Router, Predicate<String> {
	private Win32FPM fpm;

	@Override
	protected void onEnable() throws Exception {
		CMap config = getConfig();

		List<String> args;
		CEntry entry = config.get("fcgi_executable");
		args = entry.getType() == Type.LIST ? entry.asList().toStringList() : Collections.singletonList(entry.asString());

		Path cgi_exe = getDataFolder().toPath().resolve(Path.of(args.get(0)));
		if (!Files.isExecutable(cgi_exe)) {
			getLogger().error("PHP可执行文件{}不存在", cgi_exe);
			getPluginManager().unloadPlugin(getDescription());
			return;
		}
		Path document_root = getDataFolder().toPath().resolve(Path.of(config.getString("document_root")));
		if (!Files.isDirectory(document_root)) {
			getLogger().error("文档根目录{}不存在", document_root);
			getPluginManager().unloadPlugin(getDescription());
			return;
		}

		fpm = new Win32FPM(config.getInteger("fcgi_process_stale_max"), config.getInteger("fcgi_process_max"), config.getInteger("fcgi_process_timeout", 600000), args);
		fpm.docRoot = document_root.toFile();
		fpm.portBase = 40000 + (int)System.nanoTime()%20000;

		var dispatcher = new TaskExecutor();
		dispatcher.setName("PHP-FPM 请求分配");
		dispatcher.start();
		dispatcher.submit(fpm);

		String pluginRoot = config.getString("dps_root");
		registerRoute(pluginRoot, this, "PermissionManager");

		PanHttp.addStatusProvider((sb, request) ->
			sb.append("PHP-FPM for Win32:\n  活动的进程: ").append(fpm.processes.size()).append('/').append(fpm.maxProcess)
			  .append("\n  排队的请求: ").append(fpm.pendingTask.size()));

		getLogger().warn("PHP服务器已在路径{} => {}上启动", fpm.docRoot, pluginRoot);
	}

	// 然而并不会生效
	@Override
	public int writeTimeout(@Nullable Request req, @Nullable Response resp) {return Integer.MAX_VALUE;}

	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		if (req.path().endsWith(".php")) return fpm.response(req, rh);

		File file = new File(fpm.docRoot, req.path());
		check: {
			if (!file.isFile()) {
				if (file.isDirectory()) {
					if (!req.path().endsWith("/") && !req.path().isEmpty()) {
						rh.code(302).header("Location", req.path()+"/");
						return null;
					}

					File[] files = file.listFiles((dir, name) -> name.equals("index.html") || name.equals("index.php"));
					if (files != null && files.length > 0 && files[0].isFile()) {
						file = files[0];
						if (file.getName().endsWith(".php")) {
							req.setPath(req.path()+file.getName());
							return fpm.response(req, rh);
						}
						break check;
					}
				}

				rh.code(404);
				return Response.httpError(404);
			}
		}

		return Response.file(req, new DiskFileInfo(file));
	}

	// (Optional) for OKRouter Prefix Delegation check
	@Override
	public boolean test(String url) {
		var file = IOUtil.safePath2(fpm.docRoot.getAbsolutePath(), url);
		return file != null && file.exists();
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		if (req.path().endsWith(".php")) fpm.checkHeader(req, cfg);
	}

	@Override
	protected void onDisable() {
		if (fpm != null) {
			fpm.stop();
			fpm.killAll();
		}
	}
}