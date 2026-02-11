package roj.plugins.dns;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.HashMap;
import roj.text.CharList;
import roj.util.BitStream;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.List;

import static roj.plugins.dns.DnsAnswer.decodeDomain;
import static roj.plugins.dns.DnsAnswer.decodeDomainPtr;
import static roj.plugins.dns.DnsQuestion.C_ANY;
import static roj.plugins.dns.DnsQuestion.C_INTERNET;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public final class DnsResponse extends DnsRequest {
	byte responseCode;
	boolean authorizedAnswer, truncated;
	HashMap<DnsResourceKey, List<DnsAnswer>> responses;
	int responseTime = (int) (System.currentTimeMillis()/1000);

	public DnsResponse(DynByteBuf r) {
		this.sessionId = (char) r.readUnsignedShort();
		BitStream br = new BitStream(r);

		boolean isQuery = br.read1Bit() == 0;
		if (isQuery) return;

		this.opcode = (byte) br.readBits(4);
		this.authorizedAnswer = br.read1Bit() != 0; // 授权回答
		this.truncated = br.read1Bit() != 0; // TC
		br.skipBits(1); // RD
		this.recursion = br.read1Bit() != 0; // RA
		// Z
		// AD // Authorization ??
		// CD // Not-Authorization data ??
		br.skipBits(3);
		this.responseCode = (byte) br.readBits(4);

		int numQueries = r.readUnsignedShort();
		int numRes = r.readUnsignedShort();
		int numARes = r.readUnsignedShort();
		int numExRes = r.readUnsignedShort();

		int offset = r.rIndex - 12;
		assert r.rIndex == 12;

		CharList sb = new CharList();

		queries = new DnsQuestion[numQueries];
		for (int i = 0; i < numQueries; i++) {
			DnsQuestion q = new DnsQuestion();
			queries[i] = q;

			q.ptr = (short) (0xC000 | r.rIndex);

			decodeDomain(r, sb);

			q.host = sb.toString();
			sb.clear();
			q.qType = r.readShort();
			q.qClass = r.readShort();
		}

		responses = new HashMap<>(numRes + numARes + numExRes);
		parseResponse(r, numRes, sb, DnsResourceKey.TYPE_ANSWER);
		parseResponse(r, numARes, sb, DnsResourceKey.TYPE_AUTHORITY_RECORD);
		parseResponse(r, numExRes, sb, DnsResourceKey.TYPE_ADDITIONAL_RECORD);
	}

	private void parseResponse(DynByteBuf r, int num, CharList sb, byte type) {
		var map = responses;
		for (int i = 0; i < num; i++) {
			var key = new DnsResourceKey();

			decodeDomainPtr(r, r, sb);
			key.host = sb.toString();
			sb.clear();
			key.type = type;

			var q = new DnsAnswer();

			q.qType = r.readShort();
			short qClass = r.readShort();
			q.qClass = qClass;
			if (qClass != C_ANY && qClass != C_INTERNET) {
				LOGGER.info("Got invalid DNS answer: {}", q);
			}
			q.TTL = r.readInt();
			q.creationTime = responseTime;

			q.parseBody(r, r.readUnsignedShort());

			map.computeIfAbsent(key, Helpers.fnArrayList()).add(q);
		}
	}

	/** OK */
	public static final byte RCODE_OK = 0;
	/** 格式错误   -- 因为请求消息格式错误无法解析该请求 */
	public static final byte RCODE_FORMAT_ERROR = 1;
	/** 服务器错误 -- 因为内部错误无法解析该请求 */
	public static final byte RCODE_SERVER_ERROR = 2;
	/** 名字错误   -- 只在权威域名服务器的响应消息中有效，请求的域不存在 */
	public static final byte RCODE_NAME_ERROR = 3;
	/** 未实现     -- 不支持请求的类型 */
	public static final byte RCODE_NOT_IMPLEMENTED = 4;
	/** 拒绝      -- 拒绝执行请求的操作 */
	public static final byte RCODE_REFUSED = 5;

	@MagicConstant(intValues = {RCODE_OK, RCODE_FORMAT_ERROR, RCODE_SERVER_ERROR, RCODE_NAME_ERROR, RCODE_NOT_IMPLEMENTED, RCODE_REFUSED})
	public byte getResponseCode() {return responseCode;}
	public HashMap<DnsResourceKey, List<DnsAnswer>> getResponses() {return responses;}
	public int getResponseTime() {return responseTime;}

	@Override
	public String toString() {
		return "DnsResponse{" + "op=" + (short) opcode + ", RD=" + recursion + ", AA=" + authorizedAnswer + ", RCode=" + responseCode + ", response=" + responses + '}';
	}
}