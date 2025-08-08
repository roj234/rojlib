package roj.plugins.ddns;

import roj.config.data.CMap;
import roj.http.HttpClient;
import roj.http.HttpRequest;
import roj.io.IOUtil;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 1:22
 */
public abstract class IpGetter {
	static HttpClient pooledRequest(String url) throws IOException {return HttpRequest.builder().url(url).executePooled(15000, 3);}

	public void loadConfig(CMap config) {}
	public abstract InetAddress[] getAddress(boolean checkV6) throws Exception;
}