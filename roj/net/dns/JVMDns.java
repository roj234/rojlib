package roj.net.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 0002 13:54
 */
public class JVMDns implements DnsService {
	public List<InetAddress> lookup(String hostname) throws UnknownHostException {
		return Arrays.asList(InetAddress.getAllByName(hostname));
	}
}
