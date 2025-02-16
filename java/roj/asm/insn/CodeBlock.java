package roj.asm.insn;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public abstract class CodeBlock {
	protected abstract boolean put(CodeWriter to, int segmentId);
	public abstract int length();

	SolidBlock setData(DynByteBuf data) { throw new UnsupportedOperationException(getClass().getName()); }
	DynByteBuf getData() { return null; }

	public CodeBlock move(AbstractCodeWriter to, int blockMoved, boolean clone) { return this; }
	protected static Label copyLabel(Label label, AbstractCodeWriter to, int blockMoved, boolean clone) {
		if (label.isUnset()) return label;

		Label tx = clone&&!to.labels.contains(label)?new Label():label;
		tx.block = (short) (label.getBlock() + blockMoved);
		tx.offset = label.offset;
		to.labels.add(tx);
		return tx;
	}

	public boolean isTerminate() { return false; }
	public boolean willJumpTo(int block, int offset) { return false; }
}