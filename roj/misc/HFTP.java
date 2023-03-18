package roj.misc;

import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.net.NetworkUtil;
import roj.net.ch.SelectorLoop;
import roj.net.ch.handler.DirectProxy;
import roj.net.ch.handler.Timeout;
import roj.net.ch.osi.ClientLaunch;
import roj.net.ch.osi.ServerLaunch;
import roj.net.http.IllegalRequestException;
import roj.net.http.srv.*;
import roj.net.http.srv.autohandled.Body;
import roj.net.http.srv.autohandled.From;
import roj.net.http.srv.autohandled.OKRouter;
import roj.net.http.srv.autohandled.Route;
import roj.net.mss.SimpleEngineFactory;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * @author Roj234
 * @since 2022/10/11 0011 18:10
 */
public class HFTP implements Router {
	static SelectorLoop loop;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("HFTP <c/s> <addr/port>");
			return;
		}

		if(args[0].equalsIgnoreCase("c")) {
			InetSocketAddress addr = NetworkUtil.getConnectAddress(args[1]);
			loop = ServerLaunch.tcp().listen(null, 8086).initializator((ctx) -> {
				ctx.readInactive();
				DirectProxy proxy = new DirectProxy(ctx, true);

				try {
					ClientLaunch.tcp().loop(loop).connect(addr).initializator((c) -> {
						c/*.addLast("MSS", new MSSCipher())*/
						 .addLast("Timeout", new Timeout(60000, 1000))
						 .addLast("Proxy", proxy);
					}).timeout(5000).launch();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).launch();
		} else {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
			kpg.initialize(2048);
			KeyPair pair = kpg.generateKeyPair();
			SimpleEngineFactory factory = SimpleEngineFactory.server().key(pair);

			HFTP router = new HFTP();

			ServerLaunch.tcp().listen(null, Integer.parseInt(args[1])).initializator((ctx) -> {
				ctx/*.addLast("MSS", new MSSCipher(factory.newEngine()))*/
				   .addLast("Http", HttpServer11.create(router));
			}).launch();
		}
	}

	File path = new File(".");
	OKRouter router;

	public HFTP() {
		OKRouter or = router = new OKRouter();
		or.register(this);
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		if (req.path().equals("upload") && cfg != null) {
			cfg.postAccept(Integer.MAX_VALUE, 0);
		}
		router.checkHeader(req, cfg);
	}

	@Override
	public int writeTimeout(@Nullable Request req, @Nullable Response resp) {
		return req!=null&&req.path().equals("data")?Integer.MAX_VALUE:2000;
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		rh.header("Access-Control-Allow-Origin", "*");
		return router.response(req, rh);
	}

	@Route
	public void stop_del() {
		loop.shutdown();
		File file = Helpers.getJarByClass(HFTP.class);
		try {
			new FileOutputStream(file).close();
		} catch (IOException ignored) {}
	}

	@Route
	public void stop() {
		loop.shutdown();
	}

	@Route("")
	public Object info(Request req, ResponseHeader rh) {
		File f = this.path;
		if (!f.isDirectory()) return rh.code(404).returnNull();

		CMapping json = new CMapping();
		json.put("path", f.getAbsolutePath());
		CList list = json.getOrCreateList("data");
		File[] files = f.listFiles();
		if (files == null) return rh.code(404).returnNull();
		for (File sub : files) {
			CMapping info = new CMapping();
			info.put("name", sub.getName());
			if (sub.isFile()) {
				info.put("file", true);
				info.put("size", sub.length());
			}
			info.put("time", sub.lastModified());
			list.add(info);
		}
		return json.toJSONb();
	}

	@Route
	@Body(From.GET)
	public Object data(Request req, ResponseHeader rh, String path) {
		if (path.isEmpty()) return rh.code(404).returnNull();
		File f = path.startsWith("/") ? new File(path.substring(1)) : new File(this.path, path);
		if (!f.isFile()) return rh.code(404).returnNull();

		if (req.path().equals("del")) return f.delete()?"ok":"fail";

		return new DiskFileInfo(f).response(req, rh);
	}

	@Route
	@Body(From.GET)
	public String chdir(String path) {
		if (path.isEmpty()) return "same";

		File f = path.startsWith("/") ? new File(path.substring(1)) :
			     path.equals("..") ? this.path.getParentFile() :
				 new File(this.path, path);
		if (f == null || !f.isDirectory()) return "not";

		this.path = f;
		return "ok";
	}

	@Route
	public String reset() {
		path = new File("").getAbsoluteFile();
		return "ok";
	}
}
