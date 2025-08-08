package roj.plugins.dns;

import roj.collect.HashMap;
import roj.util.DynByteBuf;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
final class ForwardQuery extends DnsQuery {
	int remain;
	Map<XAddr, DnsResponse> truncated;
	DnsResponse[] responses;
	long timeout;

	public ForwardQuery(DnsQuery q, int remain) {
		this.remain = remain;
		responses = new DnsResponse[remain];
		truncated = Collections.emptyMap();
		timeout = System.currentTimeMillis() + DnsServer.requestTimeout;

		sessionId = q.sessionId;
		senderIp = q.senderIp;
		senderPort = q.senderPort;
		opcode = q.opcode;
		iterate = q.iterate;
		records = q.records;
	}

	public void handle(DnsServer server, DynByteBuf r, XAddr addr) {
		try {
			DnsResponse resp = DnsServer.readDnsResponse(r, addr);
			if (resp.truncated) {
				if (truncated.isEmpty()) truncated = new HashMap<>(2);
				DnsResponse prev = truncated.putIfAbsent(addr.clone(), resp);
				if (prev != null) prev.response.putAll(resp.response);

			} else {
				DnsResponse prev = truncated.get(addr);
				if (prev != null) {
					prev.response.putAll(resp.response);
					resp = prev;
				}

				responses[--remain] = resp;
				if (remain == 0) {
					server.waiting.remove(addr);
					server.forwardQueryDone(this);
				}
			}
		} catch (Throwable e) {
			System.err.println("[Error]FQ process error: ");
			e.printStackTrace();
		}
	}
}