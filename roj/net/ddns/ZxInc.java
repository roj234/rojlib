package roj.net.ddns;

import roj.config.JSONParser;
import roj.config.data.CMapping;

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
	public InetAddress[] getAddress(boolean checkV6) throws Exception {
		CMapping url = parser.parseRaw(pooledRequest(new URL("https://v4.ip.zxinc.org/info.php?type=json")).stream()).asMap();
		InetAddress ipv4 = InetAddress.getByName(url.getDot("data.myip").asString());

		InetAddress ipv6;
		if (checkV6) {
			url = parser.parseRaw(pooledRequest(new URL("https://v6.ip.zxinc.org/info.php?type=json")).stream()).asMap();
			ipv6 = InetAddress.getByName(url.getDot("data.myip").asString());
		} else {
			ipv6 = null;
		}

		return new InetAddress[] { ipv4, ipv6 };
	}
}
