package roj.plugins.dns;

import roj.io.IOUtil;
import roj.net.Net;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.function.Function;

import static roj.plugins.dns.DnsQuestion.*;

/**
 * @author Roj234
 * @since 2026/02/14 16:30
 */
public final class DnsAnswer {
	short qType, qClass;
	int TTL; // seconds
	byte[] data;
	int creationTime;

	DnsAnswer() {}
	public DnsAnswer(short qType, byte[] data) {this(qType, C_INTERNET, data);}
	public DnsAnswer(short qType, short qClass, byte[] data) {
		this.qType = qType;
		this.qClass = qClass;
		this.data = data;
		this.TTL = -1;
	}
	/**
	 * 解压缩指针
	 */
	public void parseBody(DynByteBuf r, int len) {
		switch (qType) {
			default -> { data = r.readBytes(len); return; }
			case Q_CNAME,
				 Q_MB, Q_MD, Q_MF, Q_MG, Q_MR,
				 Q_NS, Q_PTR,
				 Q_MX, Q_SOA -> {

				// Decompress domain
			}
		}

		int i = r.wIndex();
		r.wIndex(r.rIndex + len);
		var decompressed = new ByteList(len);
		var sb = IOUtil.getSharedCharBuf();

		try {
			switch (qType) {
				case Q_CNAME,
					 Q_MB, Q_MD, Q_MF, Q_MG, Q_MR,
					 Q_NS, Q_PTR -> {
					decodeDomainPtr(r, r, sb);
					encodeDomain(decompressed, sb);
				}
				case Q_MX -> {
					int pref = r.readUnsignedShort();
					decodeDomainPtr(r, r, sb);
					encodeDomain(decompressed.putShort(pref), sb);
				}
				case Q_SOA -> {
					decodeDomainPtr(r, r, sb);
					encodeDomain(decompressed, sb);
					sb.clear();

					decodeDomainPtr(r, r, sb);
					encodeDomain(decompressed, sb);
					decompressed.put(r, r.rIndex, len - r.rIndex);
				}
			}
		} finally {
			r.wIndex(i);
		}

		data = decompressed.toByteArray();
	}

	public short getqType() {return qType;}
	public short getqClass() {return qClass;}
	public boolean isOutdated(int currentTime) {return TTL != -1 && currentTime > creationTime + TTL;}
	public byte[] getData() {return data;}
	public int getTTL() {return TTL;}

	@Override
	public String toString() {
		var sb = new CharList().append(getTypeName(qType)).append(" Record{");
		if (data != null) {
			try {
				getDataRepr(qType, data, sb);
			} catch (Exception e) {
				sb.append(e);
			}
		}
		return sb.append(", TTL=").append(TTL).append('}').toStringAndFree();
	}

	public static String getTypeName(short qType) {
		return switch (qType) {
			case Q_A -> "A";
			case Q_AAAA -> "AAAA";
			case Q_CNAME -> "CNAME";
			case Q_MX -> "MX";
			case Q_NULL -> "NULL";
			case Q_PTR -> "PTR";
			case Q_TXT -> "TXT";
			case Q_WKS -> "WKS";
			default -> String.valueOf(qType);
		};
	}
	public static void getDataRepr(short qType, byte[] data, CharList sb) {
		var r = new ByteList(data);
		switch (qType) {
			default -> sb.append(new String(data));
			case Q_A, Q_AAAA -> sb.append(Net.bytes2ip(data));
			case Q_CNAME,
				 Q_MB, Q_MD, Q_MF, Q_MG, Q_MR,
				 Q_NS, Q_PTR -> {

				decodeDomain(r, sb);
			}
			case Q_HINFO -> {
				sb.append("CPU: ").append(readCharacter(r)).append(", OS: ").append(readCharacter(r));
			}
			case Q_MX -> {
				int pref = r.readUnsignedShort();
				sb.append("Preference: ").append(Integer.toString(pref)).append(", Ex: ");
				decodeDomain(r, sb);
			}
			case Q_SOA -> {
				sb.append("Src: ");
				decodeDomain(r, sb);
				decodeDomain(r, sb.append(", Owner: "));
				sb.append(", ZoneId(SERIAL): ").append(r.readUnsignedInt())
				  .append(", ZoneTtl(REFRESH): ").append(r.readUnsignedInt())
				  .append(", Retry: ").append(r.readUnsignedInt())
				  .append(", Expire: ").append(r.readUnsignedInt())
				  .append(", MinTtlInServer: ").append(r.readUnsignedInt());
			}
			case Q_TXT -> {
				sb.append('[');
				while (r.readableBytes() > 0) {
					sb.append(readCharacter(r)).append(", ");
				}
				sb.setLength(sb.length() - 2);
				sb.append(']');
			}
			case Q_WKS -> {
				sb.append("Address: ").append(Net.bytes2ip(data));
				r.rIndex = 4;
				sb.append(", Proto: ").append(r.readUnsignedByte()).append(", BitMap(").append(r.readableBytes()).append("): <UNPARSED>");
			}
			case Q_NULL -> {
			}
		}
	}

	public static List<DnsAnswer> findAnswer(List<DnsAnswer> records, List<DnsAnswer> output, short qType, Function<String, List<DnsAnswer>> lookup) {
		for (int i = 0; i < records.size(); i++) {
			DnsAnswer answer = records.get(i);

			boolean match = false;
			if (qType == Q_ANY) {
				match = true;
			} else if (qType == Q_MAILB) {
				// MB MG MR
				if (answer.qType >= Q_MB && answer.qType <= Q_MR) match = true;
			} else if (answer.qType == qType) {
				match = true;
			}

			if (match) output.add(answer);

			if (answer.qType == Q_CNAME && qType != Q_CNAME) {
				CharList sb = IOUtil.getSharedCharBuf();
				sb.clear();
				DnsAnswer.decodeDomain(new ByteList(answer.data), sb);
				String domain = sb.toString();

				// 防死循环：如果该域名已经处理过，跳过
				//if (visited.add(domain)) {}
				List<DnsAnswer> nextRecords = lookup.apply(domain);
				if (nextRecords != null && !nextRecords.isEmpty()) {
					findAnswer(nextRecords, output, qType, lookup);
				}
			}
		}

		return output;
	}

	public static void decodeDomainPtr(DynByteBuf r, DynByteBuf ptrBuf, CharList sb) {
		int len;
		while (true) {
			len = r.readUnsignedByte();
			if ((len & 0xC0) != 0) {
				if ((len & 0xC0) != 0xC0) throw new RuntimeException("Illegal label length " + Integer.toHexString(len));
				int ri = ptrBuf.rIndex;
				ptrBuf.rIndex = ((len & ~0xC0) << 8) | r.readUnsignedByte();
				decodeDomainPtr(ptrBuf, ptrBuf, sb);
				ptrBuf.rIndex = ri + (r == ptrBuf ? 1 : 0);
				return;
			}
			if (len == 0) break;
			sb.append(r.readUTF(len)).append(".");
		}
		sb.setLength(sb.length() - 1);
	}

	public static void decodeDomain(DynByteBuf r, CharList sb) {
		int len;
		while (true) {
			len = r.readUnsignedByte();
			if ((len & 0xC0) != 0) throw new IllegalArgumentException("Illegal label length " + len);
			if (len == 0) break;
			sb.append(r.readUTF(len)).append('.');
		}
		sb.setLength(sb.length() - 1);
	}

	public static String readCharacter(DynByteBuf r) {return r.readUTF(r.readUnsignedByte());}
}
