package roj.asm.visitor;

/**
 * @author Roj234
 * @since 2023/10/5 1:50
 */
final class FirstSegment extends Segment {
	private final int length;
	FirstSegment(int len) { length = len; }
	protected boolean put(CodeWriter to) { return false; }
	protected int length() { return length; }
	public String toString() { return "First("+length+')'; }
}
