package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.attr.Attribute;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/18 0018 20:27
 */
public interface AttributeReader extends Attributed {
	Attribute parseAttribute(ConstantPool pool, DynByteBuf r, String name, int length);
}
