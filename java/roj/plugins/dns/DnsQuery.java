package roj.plugins.dns;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public class DnsQuery {
	char sessionId;
	InetAddress senderIp;
	char senderPort, opcode;
	boolean iterate;

	DnsRecord[] records;

	@Override
	public String toString() {
		return "DnsQuery{" + senderIp + ":" + (int) senderPort + ", op=" + (int) opcode + ", RD=" + iterate + ", " + Arrays.toString(records) + '}';
	}
}