package roj.plugin;

import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.net.http.HttpUtil;
import roj.net.http.server.*;
import roj.text.CharList;
import roj.text.Template;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/7/22 23:18
 */
public class PathIndexRouter implements Router, Predicate<String> {
	public final String path;
	public boolean hideAbsolutePath;
	public PathIndexRouter(String path) {this(new File(path));}
	public PathIndexRouter(File path) {this.path = path.getAbsolutePath();}

	private static ZipFile zf;
	private static FileInfo js, css;
	private static Template template;

	static {
		try {
			zf = new ZipFile(new File(Panger.getInstance().getPluginFolder(), "Core/resource-pi.zip"));
			js = new ZipRouter.ZipFileInfo(zf, zf.getEntry("pi.js"));
			css = new ZipRouter.ZipFileInfo(zf, zf.getEntry("pi.css"));
			template = Template.compile(IOUtil.readString(zf.getStream("pi.html")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path();
		if (url.equals("@@pi.js")) return Response.file(req, js);
		else if (url.equals("@@pi.css")) return Response.file(req, css);

		File file = new File(path, url);

		if (file.isDirectory()) {
			var index = new File(file, "index.html");
			if (index.isFile()) file = index;
			else if (url.endsWith("/") || url.isEmpty()) {
				var env = new MyHashMap<String, Object>();

				int length = TextUtil.split(req.path(), '/').size();
				env.put("BASE", "../".repeat(length));
				env.put("PARENT", url.isEmpty() ? "none" : "block");
				env.put("LOC", Tokenizer.addSlashes(hideAbsolutePath ? url.isEmpty()?"/":url : file.getPath()));

				var tmp = IOUtil.getSharedCharBuf().append('[');

				File[] files = file.listFiles();
				if (files != null && files.length > 0) {
					tmp.append('[');
					for (int i = 0; i < files.length;) {
						file = files[i];
						var attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

						Tokenizer.addSlashes(tmp.append('"'), file.getName());
						if (attr.isDirectory()) tmp.append('/');
						tmp.append("\",").append(attr.lastModifiedTime().to(TimeUnit.SECONDS));
						if (attr.isRegularFile()) TextUtil.scaledNumber1024(tmp.append(',').append(attr.size()).append(",\""), attr.size()).append('"');
						tmp.append(++i == files.length ? "]" : "],[");
					}
				}
				env.put("FILE", tmp.append(']'));

				var ob = new CharList();
				template.format(env, ob);
				return Response.html(ob);
			}
		}
		if (!file.isFile()) {
			rh.code(404);
			return Response.httpError(HttpUtil.NOT_FOUND);
		}

		rh.code(200).header("cache-control", HttpUtil.CACHED_REVALIDATE);
		return Response.file(req, new DiskFileInfo(file));
	}

	// (Optional) for OKRouter Prefix Delegation check
	@Override
	public boolean test(String url) {
		if (url.startsWith("@@pi.")) return true;

		var file = IOUtil.safePath2(path, url);
		return file != null && file.exists();
	}
}