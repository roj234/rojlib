package roj.plugins.ddns;

import roj.config.JSONParser;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.http.HttpRequest;
import roj.net.http.SyncHttpClient;
import roj.ui.CLIUtil;
import roj.util.ByteList;

import java.net.InetAddress;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class CTLightCat extends IpGetter {
	private String accessToken;
	private long refreshTime;

	private boolean refreshAccessToken() {
		try {
			ByteList body = IOUtil.getSharedByteBuf().putAscii("username=useradmin&psd=").putAscii(URIUtil.encodeURIComponent(pass));
			SyncHttpClient shc = HttpRequest.nts()
				.url(new URL("http://"+catUrl+"/cgi-bin/luci"))
				.header("Content-Type","application/x-www-form-urlencoded")
				.body(ByteList.wrap(body.toByteArray()))
				.executePooled();

			if (shc.head().getCode() == 302) {
				refreshTime = System.currentTimeMillis();
				accessToken = shc.head().getFieldValue("Set-Cookie", "sysauth");
				if (accessToken != null) return true;
			}

			CLIUtil.warning("[getAddress]无法获取AccessToken 是否密码错误？: " + shc.head());
		} catch (Exception e) {
			CLIUtil.error("getAddress", e);
		}
		return false;
	}

	private String pass, catUrl;

	@Override
	public boolean supportsV6() { return true; }

	@Override
	public void loadConfig(CMapping config) {
		pass = config.getString("CatPassword");
		catUrl = config.getString("CatUrl");
	}

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws Exception {
		if (System.currentTimeMillis() - refreshTime > 60000) refreshAccessToken();

		SyncHttpClient shc = HttpRequest.nts()
			.url(new URL("http://"+catUrl+"/cgi-bin/luci/admin/settings/gwinfo?get=part"))
			.header("Cookie", "sysauth="+accessToken)
			.executePooled();

		CMapping url = new JSONParser().parseRaw(shc.stream()).asMap();

		InetAddress WANIP = InetAddress.getByName(url.getString("WANIP")),
			WANIPv6 = InetAddress.getByName(url.getString("WANIPv6"));
		return new InetAddress[] { WANIP, WANIPv6 };
	}
}
