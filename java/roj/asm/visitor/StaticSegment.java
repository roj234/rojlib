package roj.asm.visitor;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class StaticSegment extends Segment {
	public static final Segment EMPTY = new StaticSegment(ArrayCache.BYTES);

	private Object array;
	private short off;
	private char length;

	StaticSegment() { array = new ByteList(); }
	public StaticSegment(DynByteBuf data) { array = data; }
	public StaticSegment(byte... data) { array = data; length = (char) data.length; }

	@Override
	public String toString() { return "Code("+length()+')'; }

	@Override
	protected boolean put(CodeWriter to, int segmentId) {
		if (array instanceof ByteList b) to.bw.put(b);
		else to.bw.put((byte[]) array,off&0x7FFF,length);
		return false;
	}
	@Override
	public int length() { return array.getClass() == ByteList.class ? ((ByteList)array).readableBytes() : length; }

	@Override
	public Segment move(AbstractCodeWriter from, AbstractCodeWriter to, int blockMoved, int mode) {
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

	@Override
	public boolean isTerminate() {
		DynByteBuf data = getData();
		int r = data.rIndex;
		if (data.wIndex() == r) return false;
		int b;
		do {
			b = data.getU(r);
			r += 0xF&(XInsnNodeView.OPLENGTH[b]>>>4);
		} while (data.wIndex() != r);
		return b == (Opcodes.ATHROW&0xFF) || Opcodes.category(b) == Opcodes.CATE_RETURN;
	}

	boolean compacted() { return array.getClass() == byte[].class; }
	@Override
	public DynByteBuf getData() { return array.getClass() == byte[].class ? new ByteList.Slice((byte[]) array,off&0x7FFF,length) : (DynByteBuf) array; }
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