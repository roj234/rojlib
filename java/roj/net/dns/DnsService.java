package roj.net.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/2 0002 13:53
 */
public interface DnsService {
	DnsService JVM = hostname -> Arrays.asList(InetAddress.getAllByName(hostname));

	List<InetAddress> lookup(String hostname) throws UnknownHostException;
}