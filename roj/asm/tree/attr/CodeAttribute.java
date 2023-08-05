package roj.asm.tree.attr;

import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2023/1/17 0017 18:09
 */
public interface CodeAttribute {
	void toByteArray(CodeWriter c);
}
