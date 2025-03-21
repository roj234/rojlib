package roj.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.attr.Attribute;
import roj.asm.attr.AttributeList;
import roj.asm.cp.ConstantPool;
import roj.util.TypedKey;

/**
 * @author Roj233
 * @since 2022/4/23 14:30
 */
public interface Attributed {
	default Attribute attrByName(String name) {
		AttributeList list = attributesNullable();
		return list == null ? null : (Attribute) list.getByName(name);
	}

	default <T extends Attribute> T parsedAttr(ConstantPool cp, TypedKey<T> type) {throw new UnsupportedOperationException("未实现");}

	default void putAttr(Attribute attr) {attributes().add(attr);}
	default AttributeList attributes() {throw new UnsupportedOperationException(getClass().getName());}
	@Nullable
	default AttributeList attributesNullable() {return null;}

	char modifier();
	default void modifier(int flag) { throw new UnsupportedOperationException(getClass().getName()); }
}