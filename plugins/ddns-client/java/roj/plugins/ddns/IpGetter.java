package roj.plugins.ddns;

import roj.config.node.MapValue;
import roj.http.HttpRequest;
import roj.http.HttpResponse;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 1:22
 */
public abstract class IpGetter {
	static HttpResponse pooledRequest(String url) throws IOException {return HttpRequest.builder().uri(url).executePooled(15000, 3);}

	public void loadConfig(MapValue config) {}
	public abstract InetAddress[] getAddress(boolean checkV6) throws Exception;
}