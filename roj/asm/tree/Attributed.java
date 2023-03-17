package roj.asm.tree;

import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;

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

	default void putAttr(Attribute inv) {
		attributes().add(inv);
	}

	default AttributeList attributes() {
		throw new UnsupportedOperationException(getClass().getName());
	}
	@Nullable
	default AttributeList attributesNullable() {return null;}

	char accessFlag();
	default void accessFlag(int flag) {
		throw new UnsupportedOperationException(getClass().getName());
	}

	int type();
}
