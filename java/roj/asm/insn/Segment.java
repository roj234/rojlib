package roj.asm.insn;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 12:53
 */
public abstract class Segment {
	protected abstract boolean put(CodeWriter to, int segmentId);
	public abstract int length();

	StaticSegment setData(DynByteBuf data) { throw new UnsupportedOperationException(getClass().getName()); }
	DynByteBuf getData() { return null; }

	public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) { return this; }
	protected static Label copyLabel(Label label, AbstractCodeWriter to, int blockMoved, boolean clone) {
		if (label.isUnset() || to.labels.contains(label)) return label;

		Label tx = clone?new Label():label;
		tx.block = (short) (label.getBlock() + blockMoved);
		tx.offset = label.offset;
		to.labels.add(tx);
		return tx;
	}

	public boolean isTerminate() { return false; }
	public boolean willJumpTo(int block, int offset) { return false; }
}