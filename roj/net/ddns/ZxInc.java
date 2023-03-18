package roj.net.ddns;

import roj.config.JSONParser;
import roj.config.data.CMapping;
import roj.net.http.SyncHttpClient;
import roj.ui.CmdUtil;

import java.net.InetAddress;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class ZxInc extends IpGetter {
	JSONParser parser = new JSONParser();

	@Override
	public boolean supportsV6() { return true; }

	@Override
	public InetAddress[] getAddress(boolean checkV6) {
		try {
			SyncHttpClient shc = pool.request(new URL("https://v4.ip.zxinc.org/info.php?type=json"), null);
			CMapping url = parser.parseRaw(shc.getInputStream()).asMap();
			InetAddress ipv4 = InetAddress.getByName(url.getDot("data.myip").asString());

			InetAddress ipv6;
			if (checkV6) {
				shc = pool.request(new URL("https://v6.ip.zxinc.org/info.php?type=json"), null);
				url = parser.parseRaw(shc.getInputStream()).asMap();
				ipv6 = InetAddress.getByName(url.getDot("data.myip").asString());
			} else {
				ipv6 = null;
			}

			return new InetAddress[] { ipv4, ipv6 };
		} catch (Exception e) {
			CmdUtil.error("getAddress", e);
		}
		return null;
	}

	@Override
	public void cleanup() {
		pool.closeAll();
	}
}
