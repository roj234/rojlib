package roj.net.ddns;

import roj.net.http.SyncHttpClient;
import roj.text.TextUtil;
import roj.ui.CmdUtil;

import java.net.InetAddress;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class ToolLu extends IpGetter {
	@Override
	public InetAddress[] getAddress(boolean checkV6) {
		try {
			SyncHttpClient shc = pool.request(new URL("https://ip.tool.lu/"), null);
			shc.waitFor();
			String str = shc.getAsUTF8Str();
			InetAddress WANIP = InetAddress.getByName(TextUtil.split(str, ' ').get(1));
			return new InetAddress[] { WANIP, null };
		} catch (Exception e) {
			CmdUtil.error("getAddress", e);
		}
		return null;
	}
}
