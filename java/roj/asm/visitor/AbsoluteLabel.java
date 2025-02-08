package roj.asm.visitor;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public class AbsoluteLabel extends Label {
	protected Segment segment;

	public AbsoluteLabel() {}

	public final void set(Label label) {
		super.set(label);
		segment = ((AbsoluteLabel) label).segment;
	}

	public final void set(Label label, AbstractCodeWriter cw) {
		super.set(label);
		segment = cw.segments.get(label.block);
	}

	public final void clear() {
		super.clear();
		segment = null;
	}

	boolean update(int[] sum, int len, List<Segment> segments) {
		block = (short) segments.indexOf(segment);
		int pos = value;

		int blockSize = sum[block+1] - sum[block];
		if (offset > blockSize) {
			//FIXME 谁动了我的length？
			block++;
			offset = 0;
		}

		int off = value = (char) (offset + sum[block]);
		return pos != off;
	}

	@Override
	public String toString() {
		if (isUnset()) return "<uninitialized>";
		if (block == -1) return "<unresolved "+(int)offset+">";
		return "<"+(int)value + " in [b"+block+" + "+(int)offset+"]>";
	}
}