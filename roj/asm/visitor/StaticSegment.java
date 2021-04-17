package roj.asm.visitor;

import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
final class StaticSegment extends Segment {
	final DynByteBuf data;
	int startBci;

	StaticSegment(int off) {
		data = new ByteList();
		length = -1;
		startBci = off;
	}

	StaticSegment(DynByteBuf bw, int len) {
		data = bw;
		length = len;
		// off = 0
	}

	@Override
	public boolean put(CodeWriter to) {
		to.bw.put(data);
		return false;
	}

	@Override
	protected void computeLength() {
		if (startBci > 0) length = data.wIndex();
	}

	@Override
	public String toString() {
		return "code(" + startBci + " + " + length + ')';
	}
}
