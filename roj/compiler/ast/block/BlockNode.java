package roj.compiler.ast.block;

import roj.io.IOUtil;
import roj.lavac.block.Node;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2023/11/3 0003 23:59
 */
public abstract class BlockNode implements Node {
	@Override
	public String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		toString(sb, 0);
		return sb.toString();
	}

	public abstract void toString(CharList sb, int depth);
	static void next(CharList sb, int depth) {
		while (depth-- > 0) sb.append('\t');
	}
}

