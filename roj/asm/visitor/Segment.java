package roj.asm.visitor;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public abstract class Segment {
	protected abstract boolean put(CodeWriter to);
	protected abstract int length();

	StaticSegment setData(DynByteBuf data) { throw new UnsupportedOperationException(getClass().getName()); }
	DynByteBuf getData() { return null; }

	Segment move(AbstractCodeWriter list, int blockMoved, int mode) { return this; }

	static Label copyLabel(Label label, AbstractCodeWriter list, int blockMoved, int mode) {
		if ((mode&XInsnList.REP_SHARED_NOUPDATE) != 0) return label;

		Label tx = mode==XInsnList.REP_CLONE?new Label():label;
		tx.block = (short) (label.block+blockMoved);
		tx.offset = label.offset;
		list.labels.add(tx);
		return tx;
	}
}
