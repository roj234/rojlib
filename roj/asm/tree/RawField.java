package roj.asm.tree;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.util.TypedName;

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

	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this,cp,type,attributes,Signature.FIELD); }
	public Type fieldType() { return TypeHelper.parseField(rawDesc()); }

	public int type() { return Parser.FTYPE_SIMPLE; }
	public String toString() { return AccessFlag.toString(access, AccessFlag.TS_FIELD) + fieldType() + ' ' + name.str(); }
}