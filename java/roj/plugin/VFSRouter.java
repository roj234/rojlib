package roj.plugin;

import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.http.HttpUtil;
import roj.http.server.*;
import roj.io.IOUtil;
import roj.io.vfs.VirtualFileSystem;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2024/7/22 23:18
 */
public class VFSRouter implements Router, Predicate<String> {
	protected VirtualFileSystem fs;

	public boolean hideAbsolutePath;
	public VFSRouter(String path) {this(new File(path));}
	public VFSRouter(File path) {this.fs = VirtualFileSystem.disk(path);}
	public VFSRouter(VirtualFileSystem fs) {this.fs = fs;}

	private static final FileInfo js, css;
	private static final Formatter html;

	static {
		try {
			Panger.initHttp();
			var zf = Panger.resources;
			js = new ZipRouter.ZipFileInfo(zf, zf.getEntry("assets/pi.js"));
			css = new ZipRouter.ZipFileInfo(zf, zf.getEntry("assets/pi.css"));
			html = Formatter.simple(IOUtil.readString(zf.getStream("assets/pi.html")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Content response(Request req, ResponseHeader rh) throws IOException {
		String url = req.path();
		if (url.equals("@@pi.js")) return Content.file(req, js);
		else if (url.equals("@@pi.css")) return Content.file(req, css);

		var file = fs.getPath(url);

		if (file.isDirectory()) {
			var index = fs.getPath(file, "index.html");
			if (index.isFile()) file = index;
			else if (url.endsWith("/") || url.isEmpty()) {
				var env = new MyHashMap<String, Object>();

				int length = TextUtil.split(req.path(), '/').size();
				env.put("BASE", "../".repeat(length));
				env.put("PARENT", url.isEmpty() ? "none" : "block");
				env.put("LOC", Tokenizer.escape(hideAbsolutePath ? url.isEmpty()?"/":url : file.toString()));

				var tmp = IOUtil.getSharedCharBuf().append('[');

				var virtualFiles = fs.listPath(file, Helpers.alwaysTrue());
				if (virtualFiles != null) {
					var itr = virtualFiles.iterator();
					if (itr.hasNext()) {
						tmp.append('[');
						while (true) {
							file = itr.next();

							Tokenizer.escape(tmp.append('"'), file.getName());
							if (file.isDirectory()) tmp.append('/');
							tmp.append("\",").append(file.lastModified() / 1000);
							if (file.isFile()) TextUtil.scaledNumber1024(tmp.append(',').append(file.length()).append(",\""), file.length()).append('"');

							if (!itr.hasNext()) {tmp.append(']');break;}

							tmp.append("],[");
						}
					}
				}
				env.put("FILE", tmp.append(']'));

				var ob = new CharList();
				html.format(env, ob);
				return Content.html(ob);
			}
		}
		if (!file.isFile()) {
			rh.code(404);
			return Content.httpError(HttpUtil.NOT_FOUND);
		}

		rh.code(200).header("cache-control", HttpUtil.CACHED_REVALIDATE);
		return Content.file(req, fs.toFileInfo(file));
	}

	// (Optional) for OKRouter Prefix Delegation check
	@Override
	public boolean test(String url) {
		if (url.startsWith("@@pi.")) return true;

		url = IOUtil.safePath(url);
		return fs.getPath(url).exists();
	}
}