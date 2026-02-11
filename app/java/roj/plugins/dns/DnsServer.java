package roj.plugins.dns;

import roj.config.node.ListValue;
import roj.config.node.MapValue;
import roj.io.IOUtil;
import roj.net.*;
import roj.text.FastNumberParser;
import roj.text.TextReader;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;

import static roj.plugins.dns.DnsQuestion.*;
import static roj.plugins.dns.DnsResponse.*;

/**
 * Simple DNS Server <br>
 * <a href="https://www.rfc-editor.org/info/rfc1035">RFC1035</a>
 *
 * @author solo6975
 * @since 2021/7/23 20:49
 */
public class DnsServer implements ChannelHandler {
	final ConcurrentHashMap<DnsResourceKey, List<DnsAnswer>> answerCache = new ConcurrentHashMap<>();
	public Predicate<String> blocked = Helpers.alwaysFalse();

	ConcurrentHashMap<DnsSession, ForwardedRequest> forwardQueue;
	static int requestTimeout;

	MyChannel remote, local;

	public List<InetSocketAddress> forwardDns;
	public InetSocketAddress fakeDns;

	public DnsServer(MapValue cfg, InetSocketAddress address) throws IOException {
		ServerLaunch.udp().bind(new InetSocketAddress(cfg.getInt("forwarderReceive")))
					.initializator(new ForwardedReplyHandler(this))
					.option(ServerLaunch.TCP_RECEIVE_BUFFER, 10000)
					.launch();

		ServerLaunch.udp().bind(address).initializator((ch) -> {
			// TODO
		}).option(ServerLaunch.TCP_RECEIVE_BUFFER, 10000).launch();

		forwardQueue = new ConcurrentHashMap<>();
		requestTimeout = cfg.getInt("requestTimeout");
		forwardDns = new ArrayList<>();
		ListValue list = cfg.getOrCreateList("trustedDnsServers");
		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).asString();
			int j = id.lastIndexOf(':');
			forwardDns.add(new InetSocketAddress(id.substring(0, j), FastNumberParser.parseInt(id.substring(j + 1))));
			System.out.println(forwardDns);
		}
		if (!cfg.getString("fakeDnsServer").isEmpty()) fakeDns = new InetSocketAddress(cfg.getString("fakeDnsServer"), 53);
	}

	public void launch() {

	}

	// Save / Load

	public void loadHosts(File in) throws IOException {
		try (TextReader tr = TextReader.auto(in)) {
			for (String line : tr) {
				if (line.isEmpty() || line.startsWith("#")) continue;

				int i = line.indexOf(' ');
				if (i < 0) {
					System.err.println("Invalid line "+line);
					continue;
				}

				var key = new DnsResourceKey(line.substring(i+1));
				byte[] value = Net.ip2bytes(line.substring(0, i));
				var answer = new DnsAnswer(value.length == 4 ? Q_A : Q_AAAA, value);
				answerCache.computeIfAbsent(key, Helpers.fnArrayList()).add(answer);
			}
		}
	}

	// Utility classes

	static final int OFF_FLAGS = 2, OFF_RES = 6, OFF_ARES = 8, OFF_EXRES = 10;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var pkt = (DatagramPkt) msg;
		var buf = pkt.data;
		if (buf.readableBytes() < 12) return;

		try {
			var request = new DnsRequest(buf);
			var copiedPacket = IOUtil.getSharedByteBuf().put(buf);
			if (processRequest(copiedPacket, request, forwardDns == null ? null : pkt.address)) {
				pkt.data = copiedPacket;
				local.fireChannelWrite(pkt);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean processRequest(DynByteBuf w, DnsRequest query, InetSocketAddress origin) {
		var keyForTest = new DnsResourceKey();
		Function<String, List<DnsAnswer>> lookup = s -> {
			if (blocked.test(s)) return Collections.emptyList();
			keyForTest.host = s;
			return answerCache.get(keyForTest);
		};

		int rcode = RCODE_OK;
		int sum = 0, sumA = 0, sumEx = 0;
		for (DnsQuestion question : query.getQueries()) {
			if (question.getqClass() != C_ANY && question.getqClass() != C_INTERNET) {
				System.err.println("unsupported qclass " + question.getqClass());
				continue;
			}

			var answers = lookup.apply(question.getHost());
			if (answers == null) {
				if (origin != null) {
					forwardRequest(w, query, origin);
					return false;
				} else {
					System.out.println("[Warn]上级无解析: " + keyForTest);
					rcode = forwardDns == null ? RCODE_SERVER_ERROR : RCODE_NAME_ERROR;
					continue;
				}
			}

			var realAnswers = new ArrayList<DnsAnswer>();
			DnsAnswer.findAnswer(answers, realAnswers, question.getqType(), lookup);

			int currentTime = (int) (System.currentTimeMillis() / 1000);
			for (int j = 0; j < realAnswers.size(); j++) {
				var answer = realAnswers.get(j);
				if (origin != null && answer.isOutdated(currentTime)) {
					forwardRequest(w, query, origin);
					return false;
				}
				w.putShort(question.ptr).putShort(answer.qType).putShort(question.qClass).putInt(answer.TTL).putShort(answer.data.length).put(answer.data);
			}

			switch (keyForTest.type) {
				case DnsResourceKey.TYPE_AUTHORITY_RECORD -> sumA += answers.size();
				case DnsResourceKey.TYPE_ADDITIONAL_RECORD -> sumEx += answers.size();
				default -> sum += answers.size();
			}
		}

		//  set QR, copy Opcode & RD
		w.set(OFF_FLAGS, (w.getUnsignedByte(OFF_FLAGS) & 0b10011110) | 1)
		 // set RA, copy RCODE
		 .set(OFF_FLAGS + 1, (rcode << 4) | (query.recursion ? 1 : 0))
		 .setShort(OFF_RES, sum)
		 .setShort(OFF_ARES, sumA)
		 .setShort(OFF_EXRES, sumEx);

		return true;
	}

	public void forwardRequest(DynByteBuf r, DnsRequest query, InetSocketAddress origin) {
		if (!forwardQueue.isEmpty()) {
			long timestamp = System.currentTimeMillis();
			for (var itr = forwardQueue.values().iterator(); itr.hasNext(); ) {
				var req = itr.next();
				if (timestamp > req.timeout) {
					System.out.println("[Warn]Timeout: " + req);
					itr.remove();
				}
			}
		}

		List<InetSocketAddress> target = forwardDns;

		var clientSession = new DnsSession(origin, query.sessionId);
		var request = new ForwardedRequest(query, clientSession, target.size());

		try {
			var pkt = new DatagramPkt();
			pkt.data = r;

			for (int i = 0; i < target.size(); i++) {
				DnsSession serverSession = new DnsSession(target.get(i), query.sessionId);
				while (null != forwardQueue.putIfAbsent(serverSession, request)) {
					char newId = (char) (System.nanoTime() * ThreadLocalRandom.current().nextInt());
					serverSession.sessionId = newId;
					r.setShort(0, newId);
				}
				pkt.address = target.get(i);

				int pos = r.rIndex;
				remote.fireChannelWrite(pkt);
				r.rIndex = pos;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void onForwardedRequestDone(ForwardedRequest fr) {
		forwardQueue.remove(fr.session);

		DnsResponse[] responses = fr.responses;
		//System.out.println("[Info]前向请求完成 " + Arrays.toString(responses));
		DnsResponse response = mergeResponse(responses);
		answerCache.putAll(response.responses);

		var req = fr.originalRequest;

		ByteList w = IOUtil.getSharedByteBuf();
		w.putShort(req.sessionId).putShort((1 << 15) | (req.opcode << 12) | (response.authorizedAnswer ? 1 << 11 : 0) | (response.recursion ? 1 << 8 : 0) | RCODE_OK) // flag
		 .putShort(req.queries.length).putShort(0).putInt(0);

		for (DnsQuestion q : req.queries) {
			encodeDomain(w, q.host);
			w.putShort(q.qType).putShort(q.qClass);
		}

		if (processRequest(w, req, null)) {
			var pkt = new DatagramPkt();
			pkt.address = fr.session.address;
			pkt.data = w;

			try {
				local.fireChannelWrite(pkt);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * 合并Dns消息
	 */
	protected DnsResponse mergeResponse(DnsResponse[] responses) {
		DnsResponse first = responses[0];
		for (int i = 1; i < responses.length; i++) {
			DnsResponse dnsResponse = responses[i];
			for (var entry : dnsResponse.responses.entrySet()) {
				List<DnsAnswer> answers = first.responses.computeIfAbsent(entry.getKey(), Helpers.fnArrayList());
				for (DnsAnswer answer : entry.getValue()) {
					if (!answers.contains(answer)) {
						answers.add(answer);
					}
				}
			}
		}
		return first;
	}
}