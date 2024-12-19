package roj.asm.visitor;

/**
 * @author Roj234
 * @since 2023/10/5 1:50
 */
final class FirstSegment extends Segment {
	int length;
	FirstSegment(int len) { length = len; }
	protected boolean put(CodeWriter to, int segmentId) { return false; }

	@Override
	public Segment move(AbstractCodeWriter from, AbstractCodeWriter to, int blockMoved, int mode) {
		throw new UnsupportedOperationException();
	}

	public int length() { return length; }
	public String toString() { return "First("+length+')'; }
}