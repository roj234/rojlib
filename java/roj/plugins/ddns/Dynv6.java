package roj.plugins.ddns;

import roj.collect.MyHashMap;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.URICoder;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/27 19:03
 */
final class Dynv6 implements DDNSService {
	private String HttpToken;

	private final Map<String, DDnsRecord> domain2Id = new MyHashMap<>();
	static final class DDnsRecord { InetAddress v4Addr, v6Addr;}

	@Override
	public void loadConfig(CMap config) {HttpToken = config.getString("HttpToken");}
	@Override
	public void init(Iterable<Map.Entry<String, List<String>>> managed) {}
	@Override
	public void update(Iterable<Map.Entry<String, InetAddress[]>> changed) {
		for (Map.Entry<String, InetAddress[]> entry : changed) {
			DDnsRecord record = domain2Id.get(entry.getKey());
			if (record == null) domain2Id.put(entry.getKey(), record = new DDnsRecord());

			InetAddress[] addr = entry.getValue();
			CharList sb = new CharList().append("?zone=").append(entry.getKey()).append("&token=").append(HttpToken);
			InetAddress addr1 = addr[0];
			if (addr1 != null && !addr1.equals(record.v4Addr)) {
				record.v4Addr = addr1;
				sb.append("&ipv4=").append(addr1.getHostAddress());
			}

			addr1 = addr[1];
			if (addr1 != null && !addr1.equals(record.v6Addr)) {
				record.v6Addr = addr1;
				sb.append("&ipv6=").append(URICoder.encodeURIComponent("["+addr1.getHostAddress()+"]")).append("&ipv6prefix=auto");
			}

			try {
				URLConnection conn = new URL("https://dynv6.com/api/update"+sb.toStringAndFree()).openConnection();
				InputStream in = conn.getInputStream();
				String s = IOUtil.readString(in);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}