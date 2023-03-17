package roj.asm.visitor;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public abstract class Segment {
	int length;
	abstract boolean put(CodeWriter to);

	void computeLength() {}
}
