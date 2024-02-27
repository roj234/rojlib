package roj.plugins.ddns;

import roj.net.p2p.STUN;
import roj.net.p2p.Servers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:23
 */
public class Stun extends IpGetter {
	private final int[] lastGood = {-1,-1};

	@Override
	public InetAddress[] getAddress(boolean checkV6) throws IOException {
		InetAddress[] addresses = new InetAddress[2];
		extracted(addresses, 0);
		if (checkV6) extracted(addresses, 1);
		return addresses;
	}

	private void extracted(InetAddress[] addresses, int index) throws IOException {
		Servers instance = Servers.getDefault();
		if (lastGood[index] != -1) {
			STUN.Response response = STUN.request(instance.getStunServer(lastGood[index], index > 0), 2000, STUN.UDP);
			if (response.errCode == 0) {
				addresses[index] = response.internetAddress.getAddress();
				return;
			}
		}
		for (int i = 0; i < instance.stunServerCount; i++) {
			InetSocketAddress addr = instance.getStunServer(i, index == 1);
			if (addr == null) continue;

			STUN.Response response = STUN.request(addr, 1000, STUN.UDP);
			if (response.errCode == 0) {
				addresses[index] = response.internetAddress.getAddress();
				lastGood[index] = i;
				return;
			}
		}
	}
}