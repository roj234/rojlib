package roj.plugins.dns;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
final class ForwardedRequest {
	int remain;
	DnsResponse[] responses;
	long timeout;

	DnsSession session;
	DnsRequest originalRequest;

	public ForwardedRequest(DnsRequest req, DnsSession clientSession, int serverCount) {
		this.remain = serverCount;
		responses = new DnsResponse[serverCount];
		timeout = System.currentTimeMillis() + DnsServer.requestTimeout;

		originalRequest = req;
		session = clientSession;
	}

	public void handle(DnsServer server, DynByteBuf r, DnsSession session) {
		try {
			DnsResponse resp = new DnsResponse(r);
			responses[--remain] = resp;
			if (remain == 0) {
				server.onForwardedRequestDone(this);
			}
		} catch (Throwable e) {
			System.err.println("[Error]FQ process error: ");
			e.printStackTrace();
		}
	}
}