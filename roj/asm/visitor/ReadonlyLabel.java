package roj.asm.visitor;

/**
 * @author Roj234
 * @since 2023/10/4 0004 23:53
 */
public interface ReadonlyLabel {
	boolean isValid();
	int getBlock();
	int getOffset();
	int getValue();
}
