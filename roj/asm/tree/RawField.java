package roj.asm.tree;

import roj.asm.Parser;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;

/**
 * {@link roj.asm.tree.ConstantData}中的简单字段, 不解析{@link Attribute}
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class RawField extends RawNode implements FieldNode {
	public RawField(int accesses, CstUTF name, CstUTF typeName) {
		super(accesses, name, typeName);
	}

	@Override
	public int type() {
		return Parser.FTYPE_SIMPLE;
	}

	@Override
	public Type fieldType() {
		return TypeHelper.parseField(rawDesc());
	}

	@Override
	public String toString() {
		return AccessFlag.toString(accesses, AccessFlag.TS_FIELD) + fieldType() + ' ' + name.getString();
	}
}