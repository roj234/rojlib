package roj.plugins.dns;

import org.intellij.lang.annotations.MagicConstant;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.util.BitStream;
import roj.util.DynByteBuf;

import java.util.Arrays;

import static roj.plugins.dns.DnsQuestion.C_INTERNET;

/**
 * @author Roj234
 * @since 2023/3/4 18:02
 */
public sealed class DnsRequest permits DnsResponse {
	static final Logger LOGGER = Logger.getLogger();

	protected DnsRequest() {}
	public DnsRequest(char sessionId, int opcode, boolean recursion, DnsQuestion[] queries) {
		this.sessionId = sessionId;
		this.opcode = (byte) opcode;
		this.recursion = recursion;
		this.queries = queries;
	}
	public DnsRequest(DynByteBuf r) {
		sessionId = (char) r.readUnsignedShort();

		BitStream br = new BitStream(r);

		boolean isQuery = br.read1Bit() == 0;
		if (isQuery) return;

		opcode = (byte) br.readBits(4);
		br.skipBits(2); // AA, TC
		recursion = br.read1Bit() == 1;

		r.rIndex++; // RA, Z, AD, CD, RCode(4)

		// Not supported now
		if (opcode > 2) return;

		int numQueries = r.readUnsignedShort();
		int numAnswers = r.readUnsignedShort();
		int numAuthorityRecords = r.readUnsignedShort();

		// Not supported now
		int numAdditionalRecords = r.readUnsignedShort();

		if (numAnswers != 0 || numAuthorityRecords != 0) return;

		queries = new DnsQuestion[numQueries];
		CharList sb = IOUtil.getSharedCharBuf();

		for (int i = 0; i < numQueries; i++) {
			DnsQuestion q = new DnsQuestion();
			queries[i] = q;

			q.ptr = (short) (0xC000 | r.rIndex);

			DnsAnswer.decodeDomain(r, sb);
			q.host = sb.toString();
			sb.clear();

			q.qType = r.readShort();

			if ((q.qClass = r.readShort()) != C_INTERNET) {
				LOGGER.info("Got invalid DNS query: {}", q);
			}
		}

		valid = true;
	}

	char sessionId;
	byte opcode;
	boolean recursion;
	boolean valid;

	DnsQuestion[] queries;

	/** 标准查询 */
	public static final byte
			OP_QUERY = 0,
	/** 反向查询 */
			OP_IQUERY = 1,
	/** DNS状态请求 */
			OP_STATUS = 2,
	/** DNS域更新请求 */
			OP_UPDATE = 5;

	@MagicConstant(intValues = {OP_QUERY, OP_IQUERY, OP_STATUS, OP_UPDATE})
	public byte getOpcode() {return opcode;}

	/**
	 * 如果可行的话，执行递归查询
	 */
	public boolean isRecursion() {return recursion;}

	/**
	 * 请求是否合法
	 */
	public boolean isValid() {return valid;}

	public DnsQuestion[] getQueries() {return queries;}

	@Override
	public String toString() {
		return "DnsQuery{" +  "op=" + opcode + ", RD=" + recursion + ", " + Arrays.toString(queries) + '}';
	}
}