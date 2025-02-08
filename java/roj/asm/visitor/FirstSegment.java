package roj.asm.visitor;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/10/5 1:50
 */
final class FirstSegment extends Segment {
	private final DynByteBuf buf;
	private final int off;
	private int length;
	FirstSegment(DynByteBuf buf, int off) {
		this.buf = buf; this.off = off;
		this.length = buf.wIndex() - off;
	}

	@Override protected boolean put(CodeWriter to, int segmentId) {length = buf.wIndex() - off;return false;}
	@Override public int length() {return length;}
	@Override public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {throw new UnsupportedOperationException();}
	@Override public String toString() {return "First("+length()+')';}
}