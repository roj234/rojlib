package roj.plugins.ddns;

import roj.config.data.CMap;
import roj.http.HttpClient;
import roj.http.HttpRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedByInterruptException;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:22
 */
public abstract class IpGetter {
	static HttpClient pooledRequest(String url) throws IOException {
		HttpClient client = HttpRequest.builder().url(url).executePooled(15000, 3);
		try {
			client.waitFor();
		} catch (InterruptedException e) {
			ClosedByInterruptException ex = new ClosedByInterruptException();
			ex.setStackTrace(e.getStackTrace());
			throw ex;
		}
		return client;
	}

	public void loadConfig(CMap config) {}
	public abstract InetAddress[] getAddress(boolean checkV6) throws Exception;
}