package roj.plugins.ddns;

import roj.config.JSONParser;
import roj.config.data.CMap;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 1:23
 */
final class ZxInc extends IpGetter {
	JSONParser parser = new JSONParser();

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws Exception {
		CMap url = parser.parse(pooledRequest("https://v4.ip.zxinc.org/info.php?type=json").stream()).asMap();
		InetAddress ipv4 = InetAddress.getByName(url.getDot("data.myip").asString());

		InetAddress ipv6;
		if (checkV6) {
			url = parser.parse(pooledRequest("https://v6.ip.zxinc.org/info.php?type=json").stream()).asMap();
			ipv6 = InetAddress.getByName(url.getDot("data.myip").asString());
		} else {
			ipv6 = null;
		}

		return new InetAddress[] { ipv4, ipv6 };
	}
}