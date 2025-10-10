package roj.plugins.ddns;

import roj.config.JsonParser;
import roj.config.node.MapValue;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 1:23
 */
final class ZxInc extends IpGetter {
	JsonParser parser = new JsonParser();

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws Exception {
		MapValue url = parser.parse(pooledRequest("https://v4.ip.zxinc.org/info.php?type=json").stream()).asMap();
		InetAddress ipv4 = InetAddress.getByName(url.query("data.myip").asString());

		InetAddress ipv6;
		if (checkV6) {
			url = parser.parse(pooledRequest("https://v6.ip.zxinc.org/info.php?type=json").stream()).asMap();
			ipv6 = InetAddress.getByName(url.query("data.myip").asString());
		} else {
			ipv6 = null;
		}

		return new InetAddress[] { ipv4, ipv6 };
	}
}