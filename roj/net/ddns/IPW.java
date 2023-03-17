package roj.net.ddns;

import roj.net.http.SyncHttpClient;
import roj.ui.CmdUtil;

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
	public InetAddress[] getAddress(boolean checkV6) {
		try {
			SyncHttpClient shc = pool.request(new URL("https://4.ipw.cn/"), null);
			shc.waitFor();
			InetAddress ipv4 = InetAddress.getByName(shc.getAsUTF8Str());

			InetAddress ipv6;
			if (checkV6) {
				shc = pool.request(new URL("https://6.ipw.cn/"), null);
				shc.waitFor();
				ipv6 = InetAddress.getByName(shc.getAsUTF8Str());
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
