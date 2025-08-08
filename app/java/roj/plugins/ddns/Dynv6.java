package roj.plugins.ddns;

import org.jetbrains.annotations.Nullable;
import roj.collect.HashMap;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.URICoder;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Roj234
 * @since 2023/1/27 19:03
 */
final class Dynv6 implements IpMapper {
	private String token;

	private final HashMap<String, State> domain2Id = new HashMap<>();
	private static final class State { InetAddress v4Addr, v6Addr;}

	@Override
	public void init(CMap config) {
		token = config.getString("HttpToken");
		for (var entry : config.getList("Hosts")) {
			domain2Id.put(entry.asString(), new State());
		}
	}

	@Override
	public void update(@Nullable InetAddress addr4, @Nullable InetAddress addr6) {
		for (var entry : domain2Id.entrySet()) {
			var record = entry.getValue();

			var sb = new CharList("https://dynv6.com/api/update?hostname=").append(entry.getKey()).append("&token=").append(token);

			if (addr4 != null && !addr4.equals(record.v4Addr)) {
				record.v4Addr = addr4;
				sb.append("&ipv4=").append(addr4.getHostAddress());
			}

			if (addr6 != null && !addr6.equals(record.v6Addr)) {
				record.v6Addr = addr6;
				sb.append("&ipv6=").append(URICoder.encodeURIComponent("["+addr6.getHostAddress()+"]")).append("&ipv6prefix=auto");
			}

			try {
				URLConnection conn = new URL(sb.toStringAndFree()).openConnection();
				InputStream in = conn.getInputStream();
				String s = IOUtil.readString(in);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}