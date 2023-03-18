package roj.net.ddns;

import roj.text.TextUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class ToolLu extends IpGetter {
	@Override
	public InetAddress[] getAddress(boolean checkV6) throws IOException {
		String str = pooledRequest(new URL("https://ip.tool.lu/")).utf();
		InetAddress WANIP = InetAddress.getByName(TextUtil.split(str, ' ').get(1));
		return new InetAddress[] { WANIP, null };
	}
}
