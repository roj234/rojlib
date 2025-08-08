package roj.asm;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.asm.attr.Attribute;
import roj.asm.attr.AttributeList;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.ConstantPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

/**
 * @author Roj233
 * @since 2022/4/23 14:30
 */
public interface Attributed {
	@Contract(pure = true) default @Nullable Attribute getAttribute(String name) {
		var attributes = attributesNullable();
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}
	default UnparsedAttribute getAttribute(ConstantPool cp, String name) {
		var list = attributesNullable();
		if (list == null) return null;

		var attr = (Attribute)list.getByName(name);
		if (attr == null || attr.writeIgnore()) return null;
		if (attr instanceof UnparsedAttribute up) return up;

		ByteList buf = AsmCache.buf();
		attr.toByteArrayNoHeader(buf, cp);
		var out = new UnparsedAttribute(attr.name(), buf.readableBytes() == 0 ? null : ((DynByteBuf) buf).toByteArray());
		AsmCache.buf(buf);
		list.add(out);
		return out;
	}
	default <T extends Attribute> T getAttribute(ConstantPool cp, TypedKey<T> type) {throw new UnsupportedOperationException("未实现");}
	default void addAttribute(Attribute attr) {attributes().add(attr);}

	default AttributeList attributes() {throw new UnsupportedOperationException(getClass().getName());}
	@Contract(pure = true) default @Nullable AttributeList attributesNullable() {return null;}

	char modifier();
	default void modifier(int flag) { throw new UnsupportedOperationException(getClass().getName()); }
}