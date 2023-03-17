package roj.net.ddns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class IPW extends IpGetter {
	@Override
	public boolean supportsV6() { return true; }

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws IOException {
		InetAddress ipv4 = InetAddress.getByName(pooledRequest(new URL("https://4.ipw.cn/")).utf());
		InetAddress ipv6 = checkV6 ? InetAddress.getByName(pooledRequest(new URL("https://6.ipw.cn/")).utf()) : null;

		return new InetAddress[] { ipv4, ipv6 };
	}
}
