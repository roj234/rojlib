package roj.net.ddns;

import roj.config.JSONParser;
import roj.config.data.CMapping;
import roj.config.word.StreamAsChars;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.http.Headers;
import roj.net.http.IHttpClient;
import roj.net.http.SyncHttpClient;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.net.InetAddress;
import java.net.URL;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class CTLightCat extends IpGetter {
	private String accessToken;
	private long refreshTime;

	private final Consumer<IHttpClient> APPLY_TOKEN = (hc) -> hc.header("Cookie", "sysauth="+accessToken);
	private boolean refreshAccessToken() {
		try {
			SyncHttpClient shc = pool.request(new URL("http://"+catUrl+"/cgi-bin/luci"), hc -> {
				ByteList b = IOUtil.getSharedByteBuf().putAscii("username=useradmin&psd=").putAscii(URIUtil.encodeURIComponent(pass));
				hc.method("POST").body(ByteList.wrap(b.toByteArray())).header("Content-Type","application/x-www-form-urlencoded");
			});
			shc.waitFor();
			if (shc.getHead().getCode() == 302) {
				refreshTime = System.currentTimeMillis();

				accessToken = Headers.decodeOneValue(shc.getHead().getHeaderField("Set-Cookie"), "sysauth");
				if (accessToken != null) return true;
			}
			CmdUtil.warning("[getAddress]无法获取AccessToken 是否密码错误？: " + shc.getHead());
		} catch (Exception e) {
			CmdUtil.error("getAddress", e);
		}
		return false;
	}

	private String pass, catUrl;

	@Override
	public boolean supportsV6() {
		return true;
	}

	@Override
	public void loadConfig(CMapping config) {
		pass = config.getString("CatPassword");
		catUrl = config.getString("CatUrl");
	}

	@Override
	public InetAddress[] getAddress(boolean checkV6) {
		try {
			if (System.currentTimeMillis() - refreshTime > 60000) refreshAccessToken();

			SyncHttpClient shc = pool.request(new URL("http://"+catUrl+"/cgi-bin/luci/admin/settings/gwinfo?get=part"), APPLY_TOKEN);
			CMapping url = JSONParser.parses(new StreamAsChars(shc.getInputStream())).asMap();

			InetAddress WANIP = InetAddress.getByName(url.getString("WANIP")),
						WANIPv6 = InetAddress.getByName(url.getString("WANIPv6"));
			return new InetAddress[] { WANIP, WANIPv6 };
		} catch (Exception e) {
			CmdUtil.error("getAddress", e);
		}
		return null;
	}
}
