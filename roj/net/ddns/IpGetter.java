package roj.net.ddns;

import roj.config.data.CMapping;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:22
 */
public abstract class IpGetter {
	protected final HttpPool pool = new HttpPool(2, 2000);

	public boolean supportsV6() {
		return false;
	}

	public void loadConfig(CMapping config) {}

	public abstract InetAddress[] getAddress(boolean checkV6);

	public void cleanup() {
		pool.closeAll();
	}
}
