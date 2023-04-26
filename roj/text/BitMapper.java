package roj.text;

import roj.collect.Int2IntMap;
import roj.io.IOUtil;
import roj.util.BitWriter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/9/4 0004 22:15
 */
public class BitMapper extends BitWriter {
	public final int bits;
	protected final CharSequence cw;
	protected final Int2IntMap ccw;

	public BitMapper(CharSequence cwmap) {
		cw = cwmap;
		bits = Integer.numberOfTrailingZeros(cwmap.length());
		ccw = new Int2IntMap(cwmap.length());
		for (int i = 0; i < cwmap.length(); i++) {
			ccw.putInt(cwmap.charAt(i), i);
		}
	}

	public String encode(CharSequence cs) {
		return encode(IOUtil.getSharedByteBuf().putZhCnData(cs)).toString();
	}

	public CharList encode(DynByteBuf buf) {
		CharList out = IOUtil.getSharedCharBuf();

		out.ensureCapacity(1 + readableBits() / bits);

		reset(buf);
		while (readableBits() > bits) {
			out.append(cw.charAt(readBit(bits)));
		}
		if (buf.readableBytes() > 0) {
			out.append(cw.charAt(readBit(readableBits())));
		}

		return out;
	}

	public ByteList decodeR(CharSequence cs) {
		ByteList buf = IOUtil.getSharedByteBuf();
		buf.ensureCapacity((cs.length() * bits) >>> 3);

		reset(buf);

		for (int i = 0; i < cs.length(); i++) {
			Int2IntMap.Entry entry = ccw.getEntry(cs.charAt(i));
			if (entry == null) {
				System.err.println("no such code, at index " + i + " of char " + cs.charAt(i));
				break;
			}
			writeBit(bits, entry.v);
		}
		endBitWrite();

		return buf;
	}

	public String decode(CharSequence cs) {
		ByteList v = decodeR(cs);
		return v.readZhCn(v.readableBytes());
	}
}
