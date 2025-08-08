package roj.plugins.dns;

import roj.collect.HashMap;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public final class DnsResponse extends DnsQuery {
	boolean authorizedAnswer, truncated;
	byte responseCode;
	HashMap<RecordKey, List<DnsServer.Record>> response;

	@Override
	public String toString() {
		return "DnsResponse{" + "sender=" + senderIp + ":" + (short) senderPort + ", op=" + (short) opcode + ", RD=" + iterate + ", AA=" + authorizedAnswer + ", RCode=" + responseCode + ", response=" + response + '}';
	}
}