package roj.asm;

import org.jetbrains.annotations.Contract;
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
	@Contract(pure = true) default @Nullable Attribute getRawAttribute(String name) {
		var attributes = attributesNullable();
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}
	default <T extends Attribute> T getAttribute(ConstantPool cp, TypedKey<T> type) {throw new UnsupportedOperationException("未实现");}
	default void addAttribute(Attribute attr) {attributes().add(attr);}

	default AttributeList attributes() {throw new UnsupportedOperationException(getClass().getName());}
	@Contract(pure = true) default @Nullable AttributeList attributesNullable() {return null;}

	char modifier();
	default void modifier(int flag) { throw new UnsupportedOperationException(getClass().getName()); }
}