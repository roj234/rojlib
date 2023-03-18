package roj.asm.tree;

import roj.asm.cst.ConstantPool;

/**
 * @author Roj234
 * @since 2022/11/18 0018 20:27
 */
public interface AttributeReader extends Attributed {
	void parseAttributes(ConstantPool cp);
}
