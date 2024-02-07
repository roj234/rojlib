package roj.asm.visitor;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public abstract class Segment {
	protected abstract boolean put(CodeWriter to, int segmentId);
	public abstract int length();

	StaticSegment setData(DynByteBuf data) { throw new UnsupportedOperationException(getClass().getName()); }
	DynByteBuf getData() { return null; }

	public Segment move(AbstractCodeWriter from, AbstractCodeWriter to, int blockMoved, int mode) { return this; }

	static Label copyLabel(Label label, AbstractCodeWriter from, AbstractCodeWriter to, int blockMoved, int mode) {
		if ((mode&XInsnList.REP_SHARED_NOUPDATE) != 0 || !from.labels.contains(label)) return label;

		Label tx = mode==XInsnList.REP_CLONE?new Label():label;
		tx.block = (short) (label.block+blockMoved);
		tx.offset = label.offset;
		to.labels.add(tx);
		return tx;
	}

	public boolean isTerminate() { return false; }
	public boolean willJumpTo(int block, int offset) { return false; }
}