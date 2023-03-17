package roj.kscript.util;

import roj.kscript.asm.Node;

/**
 * @author Roj234
 * @since 2021/4/17 22:03
 */
public final class LineInfo {
	public int line;
	public Node node;

	@Override
	public String toString() {
		return "{" + node + ": " + line + '}';
	}
}
