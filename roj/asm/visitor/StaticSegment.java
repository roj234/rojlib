package roj.asm.visitor;

import roj.asm.AsmShared;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
final class StaticSegment extends Segment {
	private Object array = new ByteList();
	private short off;
	private char length;

	StaticSegment() {}

	@Override
	public String toString() { return "Code("+(int)length+')'; }

	@Override
	protected boolean put(CodeWriter to) {
		if (array.getClass() == ByteList.class) to.bw.put((ByteList)array);
		else to.bw.put((byte[]) array,off&0x7FFF,length);
		return false;
	}
	@Override
	protected int length() { return array.getClass() == ByteList.class ? ((ByteList)array).readableBytes() : length; }

	@Override
	Segment move(AbstractCodeWriter list, int blockMoved, int mode) {
		if (mode==XInsnList.REP_CLONE) {
			if (compacted()) {
				off |= 0x8000;
				return this;
			}

			StaticSegment b = new StaticSegment();
			b.array = new ByteList(((ByteList) array).toByteArray());
			return b;
		}
		return this;
	}

	boolean compacted() { return array.getClass() == byte[].class; }
	@Override
	DynByteBuf getData() { return array.getClass() == byte[].class ? new ByteList.Slice((byte[]) array,off&0x7FFF,length) : (DynByteBuf) array; }
	StaticSegment setData(DynByteBuf b) {
		StaticSegment that = off < 0 ? new StaticSegment() : this;

		AsmShared local = AsmShared.local();

		that.length = (char) b.readableBytes();
		byte[] arr = local.getArray(that.length);
		that.array = arr;
		that.off = (short) local.getOffset(arr, that.length);

		b.read(b.rIndex, arr, that.off, that.length);
		return that;
	}
}
