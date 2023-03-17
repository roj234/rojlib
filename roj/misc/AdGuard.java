package roj.misc;

import roj.collect.TrieTreeSet;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.dns.DnsServer;
import roj.net.dns.DnsServer.Record;
import roj.net.http.srv.HttpServer11;
import roj.net.http.srv.autohandled.OKRouter;
import roj.text.CharList;
import roj.text.LineReader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;

/**
 * @author solo6975
 * @since 2022/1/1 19:20
 */
public class AdGuard {
	public static void main(String[] args) throws IOException, ParseException {
		String configFile = args.length > 0 ? args[0] : "config.json";
		CMapping cfg = JSONParser.parses(IOUtil.readUTF(new File(configFile)), JSONParser.LITERAL_KEY).asMap();

		/**
		 * Init DNS Server
		 */
		InetSocketAddress local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 53);
		System.out.println("Dns listening on " + local);
		DnsServer dns = new DnsServer(cfg, local);

		CList list = cfg.getOrCreateList("hosts");
		for (int i = 0; i < list.size(); i++) {
			dns.loadHosts(new FileInputStream(list.get(i).asString()));
		}

		TrieTreeSet tree = new TrieTreeSet();
		list = cfg.getOrCreateList("adblock");
		for (int i = 0; i < list.size(); i++) {
			CMapping map = list.get(i).asMap();
			File file = new File(map.getString("file"));
			if (System.currentTimeMillis() - file.lastModified() > map.getLong("update")) {
				String url = map.getString("url");
				if (!url.isEmpty()) {
					System.out.println("Update " + file + " via " + url);
					HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
					InputStream in = hc.getInputStream();
					try (FileOutputStream out = new FileOutputStream(file)) {
						int read;
						do {
							byte[] buf = new byte[4096];
							read = in.read(buf);
							out.write(buf, 0, read);
						} while (read > 0);
					} finally {
						hc.disconnect();
					}
				}
			}

			CharList tmp = new CharList();
			for (String ln : new LineReader(new FileInputStream(file))) {
				if (ln.isEmpty() || ln.startsWith("!")) continue;

				tmp.clear();
				tmp.append(ln).replace("@@", "").replace("|", "").replace("^", "");
				tree.add(tmp.toString());
			}
		}
		if (!tree.isEmpty()) {
			dns.blocked = s -> {
				int i = 0;
				do {
					// e.qq.com does not matches image.qq.com
					// but matches abc.e.qq.com
					if (tree.contains(s, i, s.length())) {
						if (i != 0) System.out.println("Matched partial at " + i + " of " + s);
						return true;
					}
					i = s.indexOf('.', i) + 1;
				} while (i > 0);
				return false;
			};
		}
		dns.launch();

		/**
		 * Run HTTP Server
		 */
		int httpPort = cfg.getInteger("managePort");
		if (httpPort > 0) {
			InetSocketAddress ha = new InetSocketAddress(InetAddress.getLoopbackAddress(), httpPort);
			HttpServer11.simple(ha, 256, new OKRouter().register(dns)).launch();
			System.out.println("Http listening on " + ha);
		}

		if (cfg.containsKey("TTLFactor")) Record.ttlUpdateMultiplier = (float) cfg.getDouble("TTLFactor");

		/**
		 * Use main thread as DNS Server
		 */
		System.out.println("Welcome, to a cleaner world, " + System.getProperty("user.name", "user") + " !\n");
		try {
			roj.misc.CpFilter.registerShutdownHook();
		} catch (Error ignored) {}

		dns.launch();
	}
}
