package roj.plugins.ddns;

import roj.config.JSONParser;
import roj.config.data.CMap;
import roj.http.HttpClient;
import roj.http.HttpRequest;
import roj.io.IOUtil;
import roj.text.Escape;
import roj.ui.Terminal;
import roj.util.ByteList;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 1:23
 */
final class CTLightCat extends IpGetter {
	private String accessToken;
	private long refreshTime;

	private boolean refreshAccessToken() throws Exception {
		ByteList body = IOUtil.getSharedByteBuf().putAscii("username=useradmin&psd=").putAscii(Escape.encodeURIComponent(pass));
		HttpClient shc = HttpRequest.builder()
										.url("http://"+catUrl+"/cgi-bin/luci")
										.header("Content-Type","application/x-www-form-urlencoded")
										.body(ByteList.wrap(body.toByteArray()))
										.executePooled();

		if (shc.head().getCode() == 302) {
			refreshTime = System.currentTimeMillis();
			accessToken = shc.head().getHeaderValue("set-cookie", "sysauth");
			if (accessToken != null) return true;
		}

		Terminal.warning("[getAddress]无法获取AccessToken 是否密码错误？: "+shc.head());
		return false;
	}

	private String pass, catUrl;

	@Override
	public void loadConfig(CMap config) {
		pass = config.getString("CatPassword");
		catUrl = config.getString("CatUrl");
	}

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws Exception {
		if (System.currentTimeMillis() - refreshTime > 60000) refreshAccessToken();

		HttpClient shc = HttpRequest.builder()
			.url("http://"+catUrl+"/cgi-bin/luci/admin/settings/gwinfo?get=part")
			.header("Cookie", "sysauth="+accessToken)
			.executePooled();

		CMap url = new JSONParser().parse(shc.stream()).asMap();

		InetAddress WANIP = InetAddress.getByName(url.getString("WANIP")),
			WANIPv6 = InetAddress.getByName(url.getString("WANIPv6"));
		return new InetAddress[] { WANIP, WANIPv6 };
	}
}