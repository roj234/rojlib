package roj.plugins.ddns;

import roj.config.data.CMap;
import roj.net.http.HttpRequest;
import roj.net.http.SyncHttpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedByInterruptException;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:22
 */
public abstract class IpGetter {
	static SyncHttpClient pooledRequest(String url) throws IOException {
		SyncHttpClient client = HttpRequest.nts().url(url).executePooled(15000, 3);
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