package roj.plugins.dns;

import roj.Unused;
import roj.collect.TrieTreeSet;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.ResponseHeader;
import roj.http.server.auto.GET;
import roj.http.server.auto.OKRouter;
import roj.http.server.auto.POST;
import roj.io.IOUtil;
import roj.net.Net;
import roj.plugin.Plugin;
import roj.plugins.dns.DnsServer.Record;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

import static roj.plugins.dns.DnsServer.*;

/**
 * @author solo6975
 * @since 2022/1/1 19:20
 */
public class ADnsGuard extends Plugin {
	private DnsServer dns;

	@Override
	protected void onEnable() throws Exception {
		CMap cfg = getConfig();

		var local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 53);
		System.out.println("Dns listening on "+local);

		var dns = new DnsServer(cfg, local);

		CList list = cfg.getOrCreateList("hosts");
		for (int i = 0; i < list.size(); i++) {
			dns.loadHosts(new File(list.get(i).asString()));
		}

		TrieTreeSet tree = new TrieTreeSet();
		list = cfg.getOrCreateList("adblock");
		for (int i = 0; i < list.size(); i++) {
			CMap map = list.get(i).asMap();
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
			try (TextReader tr = TextReader.auto(file)) {
				for (String ln : tr) {
					if (ln.isEmpty() || ln.startsWith("!")) continue;

					tmp.clear();
					tmp.append(ln).replace("@@", "").replace("|", "").replace("^", "");
					tree.add(tmp.toString());
				}
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

		if (cfg.getBool("manage")) registerRoute("dns/", new OKRouter().register(dns));
		Record.ttlUpdateMultiplier = cfg.getFloat("TTLFactor", 1);

		System.out.println("Welcome, to a cleaner world, "+System.getProperty("user.name", "user")+"!");
		dns.launch();
	}

	@GET("/")
	public Content index(String msg) throws Exception {
		var sb = new StringBuilder().append("<head><meta charset='UTF-8' /><title>ADnsGuard 2.0</title></head><h1>Welcome! <br> ADnsGuard - 基于DNS的广告屏蔽器</h1>");

		if (msg != null && !msg.isEmpty()) {
			sb.append("<div style='background: 0xAA8888; margin: 16px; padding: 16px; border: #000 1px dashed; font-size: 24px; text-align: center;'>")
			  .append(Escape.decodeURI(msg))
			  .append("</div>");
		}

		sb.append("欢迎您,")
		  .append(System.getProperty("user.name", "用户"))
		  .append("! <br/><a href='stat' style='color:red;'>查看缓存的解析</a><br/><h2> 设置或者删除DNS解析: </h2>")
		  .append("<form action='set' method='post' >Url: <input type='text' name='url' /><br/>" +
			  "Type: <input type='number' name='type' /><br/>" +
			  "Content: <input type='text' name='cnt' />" +
			  "<input type='submit' value='提交' /></form>")
		  .append("<pre>Type: 1 删除, \nA(IPV4): " + Q_A + ", \nAAAA(IPV6): " + Q_AAAA + " \nCNAME: " + Q_CNAME + ", \n其他看rfc" + "</pre>")
		  .append("<h2 style='color:#eecc44;margin: 10px auto;'>Powered by ImpLib/DPS</h2>Memory: ")
		  .append(TextUtil.scaledNumber(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

		return Content.html(sb);
	}

	@GET
	public String stat() {return Unused.deepToString(resolved.entrySet());}

	@POST
	public void set(Request req, ResponseHeader rh, String url, String type, String cnt) {
		String msg = null;
		if (url == null || type == null || cnt == null) {
			msg = "缺field";
		} else {
			RecordKey key = new RecordKey();
			key.url = url;

			if (type.equals("-1")) {
				msg = (resolved.remove(key) == null) ? "不存在" : "已清除";
			} else {
				Record e = new Record();
				e.TTL = Integer.MAX_VALUE;
				short qType = (short) TextUtil.parseInt(type);
				e.qType = qType;
				if (qType == Q_A || qType == Q_AAAA) {
					e.data = Net.ip2bytes(cnt);
				} else {
					switch (qType) {
						case Q_CNAME, Q_MB, Q_MD, Q_MF, Q_MG, Q_MR, Q_NS, Q_PTR:
							DynByteBuf w = IOUtil.getSharedByteBuf();
							writeDomain(w, cnt);
							e.data = w.toByteArray();
							break;
						default:
							msg = "暂不支持" + Record.QTypeToString(qType);
					}
				}

				if (msg == null) {
					List<Record> records = resolved.computeIfAbsent(key, Helpers.fnArrayList());
					key.lock.writeLock().lock();
					records.clear();
					records.add(e);
					key.lock.writeLock().unlock();
					msg = "操作完成";
				}
			}
		}

		rh.code(302).header("Location", "./?msg="+Escape.encodeURI(msg));
	}
}