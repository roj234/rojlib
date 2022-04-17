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
        return (Attribute) attributes().getByName(name);
    }
    default AttributeList attributes() {
        throw new UnsupportedOperationException();
    }
    @Nullable
    default AttributeList attributesNullable() { return null; }

    default void accessFlag(int flag) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support set access flag");
    }
    char accessFlag();

    int type();
}
