package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.util.AttributeKey;

import javax.annotation.Nullable;

/**
 * @author Roj233
 * @since 2022/4/23 14:30
 */
public interface Attributed {
	default Attribute attrByName(String name) {
		AttributeList list = attributesNullable();
		return list == null ? null : (Attribute) list.getByName(name);
	}

	default <T extends Attribute> T parsedAttr(ConstantPool cp, AttributeKey<T> type) {
		throw new UnsupportedOperationException("未实现");
	}

	default void putAttr(Attribute inv) {
		attributes().add(inv);
	}

	default AttributeList attributes() {
		throw new UnsupportedOperationException(getClass().getName());
	}
	@Nullable
	default AttributeList attributesNullable() {return null;}

	char modifier();
	default void modifier(int flag) { throw new UnsupportedOperationException(getClass().getName()); }
}
