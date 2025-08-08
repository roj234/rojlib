package roj.compiler.asm;

import roj.asm.insn.CodeWriter;
import roj.asm.insn.Segment;

/**
 * @author Roj234
 * @since 2024/4/4 2:00
 */
final class LazyPlaceholder extends Segment {
	public static final LazyPlaceholder PLACEHOLDER = new LazyPlaceholder();
	@Override protected boolean put(CodeWriter to, int segmentId) {return false;}
	@Override public int length() {return 0;}
}
