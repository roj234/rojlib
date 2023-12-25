package roj.net.dns;

import roj.collect.MyHashMap;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.URIUtil;
import roj.net.ch.*;
import roj.net.http.Action;
import roj.net.http.srv.Request;
import roj.net.http.srv.ResponseHeader;
import roj.net.http.srv.StringResponse;
import roj.net.http.srv.autohandled.Accepts;
import roj.net.http.srv.autohandled.Body;
import roj.net.http.srv.autohandled.From;
import roj.net.http.srv.autohandled.Route;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.BitBuffer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Simple DNS Server <br>
 * <a href="https://www.rfc-editor.org/info/rfc1035">RFC1035</a>
 *
 * @author solo6975
 * @since 2021/7/23 20:49
 */
public class DnsServer implements ChannelHandler {
	static final ConcurrentHashMap<RecordKey, List<Record>> resolved = new ConcurrentHashMap<>();

	public Predicate<String> blocked = Helpers.alwaysFalse();

	ConcurrentHashMap<XAddr, ForwardQuery> waiting;
	static int requestTimeout;

	MyChannel remote, local;

	public List<InetSocketAddress> forwardDns;
	public InetSocketAddress fakeDns;

	public DnsServer(CMapping cfg, InetSocketAddress address) throws IOException {
		ServerLaunch.udp().listen(new InetSocketAddress(cfg.getInteger("forwarderReceive")))
					.initializator(new ForwardQueryHandler(this))
					.option(ServerLaunch.CHANNEL_RECEIVE_BUFFER, 10000)
					.launch();

		ServerLaunch.udp().listen(address).initializator((ch) -> {

		}).option(ServerLaunch.CHANNEL_RECEIVE_BUFFER, 10000).launch();

		waiting = new ConcurrentHashMap<>();
		requestTimeout = cfg.getInteger("requestTimeout");
		forwardDns = new ArrayList<>();
		CList list = cfg.getOrCreateList("trustedDnsServers");
		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).asString();
			int j = id.lastIndexOf(':');
			forwardDns.add(new InetSocketAddress(id.substring(0, j), TextUtil.parseInt(id.substring(j + 1))));
			System.out.println(forwardDns);
		}
		if (!cfg.getString("fakeDnsServer").isEmpty()) fakeDns = new InetSocketAddress(cfg.getString("fakeDnsServer"), 53);
	}

	public void launch() {

	}

	// Save / Load

	public void loadHosts(InputStream in) throws IOException {
		for (String line : new LineReader(in)) {
			if (line.isEmpty() || line.startsWith("#")) continue;

			int i = line.indexOf(' ');
			if (i < 0) {
				System.err.println("Invalid line " + line);
				continue;
			}

			RecordKey key = new RecordKey();
			key.url = line.substring(i + 1);

			Record record = new Record();
			byte[] value = NetworkUtil.ip2bytes(line.substring(0, i));
			record.qType = value.length == 4 ? Q_A : Q_AAAA;
			record.data = value;
			record.TTL = Integer.MAX_VALUE;

			resolved.computeIfAbsent(key, Helpers.fnArrayList()).add(record);
		}
	}

	// Utility classes

	static final byte FLAG_NORM = 1, FLAG_A = 2, FLAG_EX = 4;

	public static final class Record {
		public short qType;
		public byte[] data;
		public int TTL, timestamp = (int) (System.currentTimeMillis() / 1000);

		public static List<Record> iterateFinder(List<Record> records, List<Record> target, short qType, Function<String, List<Record>> getter) {
			for (int i = 0; i < records.size(); i++) {
				Record r = records.get(i);
				if (r.qType == Q_CNAME) {
					iterateFinder(getter.apply(QDataToString(r.qType, r.data)), target, qType, getter);
					// alias
				}
			}

			switch (qType) {
				case Q_MAILB: {
					for (int i = 0; i < records.size(); i++) {
						Record record = records.get(i);
						if (record.qType >= 7 && record.qType <= 9) {
							target.add(record);
						}
					}
					return target;
				}
				case Q_ANY: {
					target.addAll(records);
					return target;
				}
			}

			for (int i = 0; i < records.size(); i++) {
				Record record = records.get(i);
				if (record.qType == qType) {
					target.add(record);
				}
			}

			return target;
		}

		public static float ttlUpdateMultiplier = 1;

		public boolean shouldUpdate(int ts) {
			return (ts - timestamp) >= TTL * ttlUpdateMultiplier;
		}

		@Override
		public String toString() {
			return QTypeToString(qType) + " Record{" + (data == null ? null : QDataToString(qType, data)) + ", TTL=" + TTL + '}';
		}

		private static String QTypeToString(short qType) {
			switch (qType) {
				case Q_A: return "A";
				case Q_AAAA: return "AAAA";
				case Q_CNAME: return "CNAME";
				case Q_MX: return "MX";
				case Q_NULL: return "NULL";
				case Q_PTR: return "PTR";
				case Q_TXT: return "TXT";
				case Q_WKS: return "WKS";
			}

			return String.valueOf(qType);
		}

		private static String QDataToString(short qType, byte[] data) {
			DynByteBuf r = new ByteList(data);
			switch (qType) {
				case Q_A: case Q_AAAA: return NetworkUtil.bytes2ip(data);
				case Q_CNAME:
				case Q_MB: case Q_MD: case Q_MF: case Q_MG: case Q_MR:
				case Q_NS: case Q_PTR: {
					CharList sb = new CharList(30);
					try {
						readDomain(r, sb);
					} catch (Throwable e) {
						e.printStackTrace();
						break;
					}
					return sb.toString();
				}
				case Q_HINFO: return "CPU: " + readCharacter(r) + ", OS: " + readCharacter(r);
				case Q_MX: {
					int pref = r.readUnsignedShort();
					CharList sb = new CharList(30).append("Preference: ").append(Integer.toString(pref)).append(", Ex: ");
					try {
						readDomain(r, sb);
					} catch (Throwable e) {
						e.printStackTrace();
						break;
					}
					return sb.toString();
				}
				case Q_SOA: {
					CharList sb = new CharList(100).append("Src: ");
					try {
						readDomain(r, sb);
						readDomain(r, sb.append(", Owner: "));
						sb.append(", ZoneId(SERIAL): ")
						  .append(Long.toString(r.readUInt()))
						  .append(", ZoneTtl(REFRESH): ")
						  .append(Long.toString(r.readUInt()))
						  .append(", Retry: ")
						  .append(Long.toString(r.readUInt()))
						  .append(", Expire: ")
						  .append(Long.toString(r.readUInt()))
						  .append(", MinTtlInServer: ")
						  .append(Long.toString(r.readUInt()));
					} catch (Throwable e) {
						e.printStackTrace();
						break;
					}
					return sb.toString();
				}
				case Q_TXT: {
					CharList sb = new CharList(100).append("[");
					try {
						while (r.readableBytes() > 0) {
							sb.append(readCharacter(r)).append(", ");
						}
						sb.setLength(sb.length() - 2);
						sb.append(']');
					} catch (Throwable e) {
						e.printStackTrace();
						break;
					}
					return sb.toString();
				}
				case Q_WKS: {
					CharList sb = new CharList(32).append("Address: ").append(NetworkUtil.bytes2ipv4(data, 0));
					r.rIndex = 4;
					return sb.append(", Proto: ").append(Integer.toString(r.readUnsignedByte())).append(", BitMap: <HIDDEN>, len = ").append(r.readableBytes()).toString();
				}
				case Q_NULL: break;
			}
			return new ByteList(data).toString();
		}

		/**
		 * 解压缩指针
		 */
		public void read(DynByteBuf rx, int len) {
			switch (qType) {
				case Q_CNAME:
				case Q_MB: case Q_MD: case Q_MF: case Q_MG: case Q_MR:
				case Q_NS: case Q_PTR:
				case Q_MX:
				case Q_SOA:
					break;
				default:
					data = rx.readBytes(len);
					return;
			}

			DynByteBuf rd = rx.slice(len);
			DynByteBuf wd = new ByteList(len);

			switch (qType) {
				case Q_CNAME:
				case Q_MB: case Q_MD: case Q_MF: case Q_MG: case Q_MR:
				case Q_NS: case Q_PTR:
					try {
						CharList sb = new CharList(30);
						readDomainEx(rd, rx, sb);
						writeDomain(wd, sb);
					} catch (Throwable e) {
						e.printStackTrace();
					}
					break;
				case Q_MX:
					try {
						int pref = rd.readUnsignedShort();
						CharList sb = new CharList(30);
						readDomainEx(rd, rx, sb);
						writeDomain(wd.putShort(pref), sb);
					} catch (Throwable e) {
						e.printStackTrace();
					}
					break;
				case Q_SOA: {
					try {
						CharList sb = new CharList(30);
						readDomainEx(rd, rx, sb);
						writeDomain(wd, sb);
						sb.clear();
						readDomainEx(rd, rx, sb);
						writeDomain(wd, sb);
						wd.put(rd, rd.rIndex, len - rd.rIndex);
					} catch (Throwable e) {
						e.printStackTrace();
					}
					break;
				}
			}

			data = wd.toByteArray();

		}
	}

	/**
	 * 由域名获得IPv4地址
	 */
	static final short Q_A = 1;
	/**
	 * 查询授权的域名服务器
	 */
	static final short Q_NS = 2;
	/**
	 * mail destination
	 *
	 * @deprecated Obsolete, use MX
	 */
	@Deprecated
	static final short Q_MD = 3;
	/**
	 * mail forwarder
	 *
	 * @deprecated Obsolete, use MX
	 */
	@Deprecated
	static final short Q_MF = 4;
	/**
	 * 查询规范名称, alias
	 */
	static final short Q_CNAME = 5;
	/**
	 * Start of authority
	 */
	static final short Q_SOA = 6;
	/**
	 * mailbox domain name (experimental)
	 */
	static final short Q_MB = 7;
	/**
	 * mail group member (experimental)
	 */
	static final short Q_MG = 8;
	/**
	 * mail rename domain name (experimental)
	 */
	static final short Q_MR = 9;
	/**
	 * null response record (experimental)
	 */
	static final short Q_NULL = 10;
	/**
	 * Well known service
	 */
	static final short Q_WKS = 11;
	/**
	 * 把IP地址转换成域名（指针记录，反向查询）
	 */
	static final short Q_PTR = 12;
	/**
	 * Host information
	 */
	static final short Q_HINFO = 13;
	/**
	 * Mail information
	 */
	static final short Q_MINFO = 14;
	/**
	 * 邮件交换记录
	 */
	static final short Q_MX = 15;
	static final short Q_TXT = 16;
	/**
	 * 由域名获得IPv6地址
	 */
	static final short Q_AAAA = 28;
	/**
	 * 传送整个区的请求 ??
	 */
	static final short Q_AXFR = 252;
	/**
	 * related: mailbox (MB, MG or MR)
	 */
	static final short Q_MAILB = 253;
	/**
	 * related: mail agent
	 *
	 * @deprecated Obsolete, use MX
	 */
	@Deprecated
	static final short Q_MAILA = 254;
	/**
	 * 所有记录
	 */
	static final short Q_ANY = 255;

	static final byte C_INTERNET = 1;
	static final byte C_ANY = (byte) 255;

	static final int OFF_FLAGS = 2;
	static final int OFF_RES = 6;
	static final int OFF_ARES = 8;
	static final int OFF_EXRES = 10;

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DatagramPkt p = ((DatagramPkt) msg);

		DynByteBuf r = p.buf;
		if (r.readableBytes() < 12) return;

		DnsQuery query = new DnsQuery();

		query.sessionId = (char) r.readUnsignedShort();

		BitBuffer br = new BitBuffer(r);
		br.skipBits(1);
		/**
		 * 请求类型，
		 * 0  QUERY  标准查询
		 * 1 IQUERY  反向查询
		 * 2 STATUS  DNS状态请求
		 * 5 UPDATE  DNS域更新请求
		 */
		query.opcode = (char) br.readBit(4);
		br.skipBits(2);
        /*int AA = r.readBit1();
        int TC = r.readBit1();*/
		/**
		 * 如果可行的话，执行递归查询
		 */
		query.iterate = br.readBit1() == 1;

		r.rIndex++;
        /*int RA = r.readBit1();
        int Z = r.readBit1();
        int AD = r.readBit1();
        int CD = r.readBit1();
        int rcode = r.readBit(4);*/

		query.senderIp = p.addr;
		query.senderPort = (char) p.port;

		int numQst = r.readUnsignedShort();
		r.rIndex += 6;

		DnsRecord[] records = query.records = new DnsRecord[numQst];
		CharList sb = IOUtil.getSharedCharBuf();

		try {
			for (int i = 0; i < numQst; i++) {
				DnsRecord q = records[i] = new DnsRecord();
				q.ptr = (short) (0xC000 | r.rIndex);

				readDomain(r, sb);

				q.url = sb.toString();
				sb.clear();
				q.qType = r.readShort();
				if ((q.qClass = r.readShort()) != 1) {
					System.out.println("[Warn]got qClass " + q.qClass);
				}
			}

			DynByteBuf w = IOUtil.getSharedByteBuf().put(r);
			if (processQuery(w, query, false)) {
				p.buf = w;
				local.fireChannelWrite(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean processQuery(DynByteBuf w, DnsQuery query, boolean isResolved) {
		RecordKey key = new RecordKey();
		Function<String, List<Record>> fn = s -> {
			if (blocked.test(s)) return Collections.emptyList();
			key.url = s;
			return resolved.getOrDefault(key, Collections.emptyList());
		};

		int sum = 0, sumA = 0, sumEx = 0;
		for (DnsRecord dReq : query.records) {
			if (blocked.test(dReq.url)) {
				System.out.println("[Dbg]Blocked " + dReq.url);
				continue;
			}

			key.url = dReq.url;
			if (dReq.qClass != C_ANY && dReq.qClass != C_INTERNET) {
				System.err.println("unsupported qclass " + dReq.qClass);
			}

			List<Record> cRecords = resolved.get(key);
			if (cRecords == null) {
				if (!isResolved) {
					forwardDnsRequest(query, w);
					return false;
				} else {
					System.out.println("[Warn]上级无解析: " + key);
					setRCode(query, w, RCODE_NAME_ERROR);
					continue;
				}
			}

			key.lock.readLock().lock();
			Record.iterateFinder(cRecords, cRecords = new ArrayList<>(), dReq.qType, fn);
			key.lock.readLock().unlock();

			int ts = (int) (System.currentTimeMillis() / 1000);
			for (int j = 0; j < cRecords.size(); j++) {
				Record cRecord = cRecords.get(j);
				if (cRecord.shouldUpdate(ts)) {
					if (!isResolved) {
						forwardDnsRequest(query, w);
						return false;
					} else {
						if (cRecord.TTL != 0) {
							System.out.println("[Warn]这TTL过期有亿点快啊: " + key + ": " + cRecord);
							//setRCode(query, w, RCODE_SERVER_ERROR);
							//continue;
						}
					}
				}
				w.putShort(dReq.ptr).putShort(cRecord.qType).putShort(dReq.qClass).putInt(cRecord.TTL).putShort(cRecord.data.length).put(cRecord.data);
			}

			switch (key.flag) {
				case FLAG_A:
					sumA += cRecords.size();
					break;
				case FLAG_EX:
					sumEx += cRecords.size();
					break;
				case FLAG_NORM:
				default:
					sum += cRecords.size();
					break;
			}
		}

		w.put(OFF_FLAGS, (byte) (w.getU(OFF_FLAGS) | 128 | 1))
		 //if(query.iterate)
		 //    bl.set(OFF_FLAGS + 1, (byte) (bl.getU(OFF_FLAGS + 1) | 128));
		 .put(OFF_RES, (byte) (sum >> 8))
		 .put(OFF_RES + 1, (byte) sum)
		 .put(OFF_ARES, (byte) (sumA >> 8))
		 .put(OFF_ARES + 1, (byte) sumA)
		 .put(OFF_EXRES, (byte) (sumEx >> 8))
		 .put(OFF_EXRES + 1, (byte) sumEx);

		//if (!isResolved) {
		//    System.out.println("[Dbg]缓存中中找到了全部, " + query);
		//}

		return true;
	}

	/**
	 * RCode -- ResponseCode <br>
	 * 0  OK <br>
	 * 1  格式错误   -- 为请求消息格式错误无法解析该请求 <br>
	 * 2  服务器错误 -- 因为内部错误无法解析该请求 <br>
	 * 3  名字错误   -- 只在权威域名服务器的响应消息中有效，请求的域不存在 <br>
	 * 4  未实现     -- 不支持请求的类型 <br>
	 * 5  拒绝      -- 拒绝执行请求的操作 <br>
	 * 6 ~ 15 保留
	 */
	static final byte RCODE_OK = 0;
	static final byte RCODE_FORMAT_ERROR = 1;
	static final byte RCODE_SERVER_ERROR = 2;
	static final byte RCODE_NAME_ERROR = 3;
	static final byte RCODE_NOT_IMPLEMENTED = 4;
	static final byte RCODE_REFUSED = 5;
	private static void setRCode(DnsQuery query, DynByteBuf clientRequest, int reason) {
		int type = clientRequest.get(3) & 0xF0;
		clientRequest.put(3, (byte) (type | (reason & 0x0F)));
	}

	public void forwardDnsRequest(DnsQuery query, DynByteBuf r) {
		//System.out.println("[Dbg]前向请求, " + query);

		List<InetSocketAddress> target = forwardDns;

		DatagramPkt pkt = new DatagramPkt();
		pkt.buf = r;

		if (target == null) {
			System.out.println("[Warn]没有前向DNS");
			setRCode(query, r, RCODE_SERVER_ERROR);
			r.put(OFF_FLAGS, (byte) (r.getU(OFF_FLAGS) | 128));

			pkt.addr = query.senderIp;
			pkt.port = query.senderPort;
			try {
				local.fireChannelWrite(pkt);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}

		ForwardQuery request = new ForwardQuery(query, target.size());
		if (!waiting.isEmpty()) {
			long t = System.currentTimeMillis();
			for (Iterator<ForwardQuery> itr = waiting.values().iterator(); itr.hasNext(); ) {
				ForwardQuery fq = itr.next();
				if (t > fq.timeout) {
					System.out.println("[Warn]Timeout: " + fq);
					itr.remove();
				}
			}
		}

		try {
			for (int i = 0; i < target.size(); i++) {
				XAddr addr = new XAddr(target.get(i), query.sessionId);
				if (null != waiting.putIfAbsent(addr, request)) {
					do {
						int myId = (int) System.nanoTime() & 65535;
						addr.id = (char) myId;
						r.putShort(0, myId);
					} while (null != waiting.putIfAbsent(addr, request));
				}
				pkt.addr = addr.addr;
				pkt.port = addr.port;

				int pos = r.rIndex;
				remote.fireChannelWrite(pkt);
				r.rIndex = pos;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static DnsResponse readDnsResponse(DynByteBuf r1, XAddr addr) {
		DnsResponse resp = new DnsResponse();

		resp.sessionId = (char) r1.readUnsignedShort();

		resp.senderIp = addr.addr;
		resp.senderPort = addr.port;

		BitBuffer r = new BitBuffer(r1);

		r.skipBits(1); // QR
		/**
		 * 请求类型
		 * 0  QUERY  标准查询
		 * 1 IQUERY  反向查询
		 * 2 STATUS  DNS状态请求
		 * 5 UPDATE  DNS域更新请求
		 */
		resp.opcode = (char) r.readBit(4);
		resp.authorizedAnswer = r.readBit1() != 0; // 授权回答
		resp.truncated = r.readBit1() != 0; // TC
		r.skipBits(1); // RD
		resp.iterate = r.readBit1() != 0; // RA
		r.skipBits(3);
		// Z
		// AD // Authorization ??
		// CD // Not-Authorization data ??
		resp.responseCode = (byte) r.readBit(4);

		r.endBitRead();

		int numQst = r1.readUnsignedShort();
		int numRes = r1.readUnsignedShort();
		int numARes = r1.readUnsignedShort();
		int numExRes = r1.readUnsignedShort();

		assert r1.rIndex == 12;

		CharList sb = new CharList();

		DnsRecord[] records = resp.records = new DnsRecord[numQst];
		for (int i = 0; i < numQst; i++) {
			DnsRecord q = records[i] = new DnsRecord();
			q.ptr = (short) (0xC000 | r1.rIndex);

			readDomain(r1, sb);

			q.url = sb.toString();
			sb.clear();
			q.qType = r1.readShort();
			q.qClass = r1.readShort();
		}

		MyHashMap<RecordKey, List<Record>> map = resp.response = new MyHashMap<>(numRes + numARes + numExRes);
		gather(r1, numRes, sb, map, FLAG_NORM);
		gather(r1, numARes, sb, map, FLAG_A);
		gather(r1, numExRes, sb, map, FLAG_EX);

		return resp;
	}

	private static void gather(DynByteBuf r, int num, CharList sb, MyHashMap<RecordKey, List<Record>> map, byte flag) {
		for (int i = 0; i < num; i++) {
			readDomainEx(r, r, sb);

			RecordKey key = new RecordKey();
			key.url = sb.toString();
			sb.clear();
			key.flag = flag;

			Record q = new Record();

			q.qType = r.readShort();
			int qClass = r.readShort();
			if (qClass != C_ANY && qClass != C_INTERNET) {
				System.err.println("unsupported qclass " + qClass);
			}
			q.TTL = r.readInt();

			q.read(r, r.readUnsignedShort());

			map.computeIfAbsent(key, Helpers.fnArrayList()).add(q);
		}
	}

	void forwardQueryDone(ForwardQuery fq) {
		DnsResponse[] responses = fq.responses;
		//System.out.println("[Info]前向请求完成 " + Arrays.toString(responses));
		DnsResponse response = mergeResponse(responses);
		resolved.putAll(response.response);

		ByteList w = IOUtil.getSharedByteBuf();
		w.putShort(fq.sessionId).putShort((1 << 15) | (fq.opcode << 12) | (response.authorizedAnswer ? 1 << 11 : 0) | (response.iterate ? 1 << 8 : 0) | RCODE_OK) // flag
		 .putShort(fq.records.length).putShort(0).putInt(0);

		for (DnsRecord req : fq.records) {
			writeDomain(w, req.url);
			w.putShort(req.qType).putShort(req.qClass);
		}

		processQuery(w, fq, true);

		DatagramPkt pkt = new DatagramPkt();
		pkt.addr = fq.senderIp;
		pkt.port = fq.senderPort;
		pkt.buf = w;

		try {
			local.fireChannelWrite(pkt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 合并Dns消息
	 */
	protected DnsResponse mergeResponse(DnsResponse[] responses) {
		return responses[0];
	}

	void handleUnknown(DynByteBuf pkt, XAddr addr) {
		try {
			DnsResponse resp = readDnsResponse(pkt, addr);
			System.out.println("[Warn] 接受未被处理的数据包 " + addr);
			resolved.putAll(resp.response);
		} catch (Throwable e) {
			System.err.println("[Error] 未被处理的'可信'数据包无效 " + addr);
			e.printStackTrace();
		}
	}

	public static void readDomainEx(DynByteBuf r, DynByteBuf rx, CharList sb) {
		int len;
		while (true) {
			len = r.readUnsignedByte();
			if ((len & 0xC0) != 0) {
				if ((len & 0xC0) != 0xC0) throw new RuntimeException("Illegal label length " + Integer.toHexString(len));
				int ri = rx.rIndex;
				rx.rIndex = ((len & ~0xC0) << 8) | r.readUnsignedByte();
				readDomainEx(rx, rx, sb);
				rx.rIndex = ri + (r == rx ? 1 : 0);
				return;
			}
			if (len == 0) break;
			sb.append(r.readUTF(len)).append(".");
		}
		sb.setLength(sb.length() - 1);
	}

	public static void readDomain(DynByteBuf r, CharList sb) {
		int len;
		while (true) {
			len = r.readUnsignedByte();
			if ((len & 0xC0) != 0) throw new IllegalArgumentException("Illegal label length " + len);
			if (len == 0) break;
			sb.append(r.readUTF(len)).append(".");
		}
		sb.setLength(sb.length() - 1);
	}

	public static void writeDomain(DynByteBuf w, CharSequence sb) {
		int prev = 0, i = 0;
		for (; i < sb.length(); i++) {
			char c = sb.charAt(i);
			assert c < 128;
			if (c == '.') {
				if (i - prev > 63) throw new IllegalArgumentException("Domain length should not larger than 63 characters");
				w.put((byte) (i - prev));
				for (int j = prev; j < i; j++) {
					w.put((byte) sb.charAt(j));
				}
				prev = i + 1;
			}
		}
		if (i - prev > 63) throw new IllegalArgumentException("Domain length should not larger than 63 characters");
		w.put((byte) (i - prev));
		if (i - prev > 0) {
			for (int j = prev; j < i; j++) {
				w.put((byte) sb.charAt(j));
			}
			w.put((byte) 0);
		}
	}

	public static String readCharacter(DynByteBuf r) {
		return r.readUTF(r.readUnsignedByte());
	}

	@Route
	public void stop() {
		System.exit(0);
	}

	@Route("")
	@Body(From.GET)
	public StringResponse index(String msg) throws Exception {
		StringBuilder sb = new StringBuilder().append("<head><meta charset='UTF-8' /><title>AsyncDns 1.2</title></head><h1>Welcome! <br> Asyncorized_MC 基于DNS的广告屏蔽器 1.2</h1>");

		if (msg != null && !msg.isEmpty()) {
			sb.append("<div style='background: 0xAA8888; margin: 16px; padding: 16px; border: #000 1px dashed; font-size: 24px; text-align: center;'>")
			  .append(URIUtil.decodeURI(msg))
			  .append("</div>");
		}

		sb.append("欢迎您,")
		  .append(System.getProperty("user.name", "用户"))
		  .append("! <br/><a href='/stat' style='color:red;'>点我列出缓存的DNS解析</a><br/>" +
			  "<a href='/stop'>点我关闭服务器</a><br/>" +
			  "<h2> 设置或者删除DNS解析: </h2>")
		  .append("<form action='/set' method='post' >Url: <input type='text' name='url' /><br/>" +
			  "Type: <input type='number' name='type' /><br/>" +
			  "Content: <input type='text' name='cnt' />" +
			  "<input type='submit' value='提交' /></form>")
		  .append("<pre>Type: 1 删除, \nA(IPV4): " + Q_A + ", \nAAAA(IPV6): " + Q_AAAA + " \nCNAME: " + Q_CNAME + ", \n其他看rfc" + "</pre>")
		  .append("<h2 style='color:#eecc44;margin: 10px auto;'>Powered by Async/v3.0</h2>Memory: ")
		  .append(TextUtil.scaledNumber(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

		return new StringResponse(sb, "text/html");
	}

	@Route
	public String stat() {
		return TextUtil.deepToString(resolved.entrySet());
	}

	@Route
	@Accepts(Action.POST)
	@Body(From.POST_KV)
	public void set(Request req, ResponseHeader rh, String url, String type, String cnt) {
		String msg = null;
		if (url == null || type == null || cnt == null) {
			msg = "缺field";
		} else {
			RecordKey key = new RecordKey();
			key.url = url;

			if (type.equals("-1")) {
				msg = (resolved.remove(key) == null) ? "不存在" : "已清除";
			} else {
				Record e = new Record();
				e.TTL = Integer.MAX_VALUE;
				short qType = (short) TextUtil.parseInt(type);
				e.qType = qType;
				if (qType == Q_A || qType == Q_AAAA) {
					e.data = NetworkUtil.ip2bytes(cnt);
				} else {
					switch (qType) {
						case Q_CNAME:
						case Q_MB:
						case Q_MD:
						case Q_MF:
						case Q_MG:
						case Q_MR:
						case Q_NS:
						case Q_PTR:
							DynByteBuf w = IOUtil.getSharedByteBuf();
							writeDomain(w, cnt);
							e.data = w.toByteArray();
							break;
						default:
							msg = "暂不支持" + Record.QTypeToString(qType);
					}
				}

				if (msg == null) {
					List<Record> records = resolved.computeIfAbsent(key, Helpers.fnArrayList());
					key.lock.writeLock().lock();
					records.clear();
					records.add(e);
					key.lock.writeLock().unlock();
					msg = "操作完成";
				}
			}
		}

		rh.code(302).header("Location", "/?msg="+URIUtil.encodeURI(msg));
	}
}