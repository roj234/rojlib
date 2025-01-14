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
	public static final StaticSegment EMPTY = new StaticSegment(ArrayCache.BYTES);

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
		if (length() == 0) return false;
		DynByteBuf buf = getData();
		return isTerminate(buf, buf.rIndex, buf.wIndex());
	}

	static boolean isTerminate(DynByteBuf buf, int start, int end) {
		int op = buf.get(end-1);
		if ((op < Opcodes.IRETURN || op > Opcodes.RETURN) && op != Opcodes.ATHROW) return false;

		// 常量池ID可能正好落在这个范围中, 所以再做一次终止检查
		do {
			op = buf.get(start);
			start += 0xF&(XInsnNodeView.OPLENGTH[op&0xFF]>>>4);
		} while (start != end);

		return (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || op == Opcodes.ATHROW;
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

		b.readFully(b.rIndex, arr, that.off, that.length);
		return that;
	}
}