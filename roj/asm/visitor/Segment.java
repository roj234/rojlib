package roj.asm.visitor;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public abstract class Segment {
	protected int length;
	protected abstract boolean put(CodeWriter to);

	protected void computeLength() {}
}
