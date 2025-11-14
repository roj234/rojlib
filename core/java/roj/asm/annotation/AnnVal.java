package roj.asm.annotation;

import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.config.node.ConfigValue;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract sealed class AnnVal extends ConfigValue permits TypeVal, EnumVal {
	public static void serialize(ConfigValue entry, DynByteBuf w, ConstantPool pool) {
		var serializer = new AnnotationEncoder();
		serializer.init(pool, w);
		entry.accept(serializer);
	}

	public static ConfigValue valueOf(Type type) {return new TypeVal(type);}
	public static ConfigValue ofEnum(String name, String name1) {return new EnumVal(name, name1);}

	public static final char STRING = 's', ENUM = 'e', ANNOTATION_CLASS = 'c', ANNOTATION = '@';

	public static ConfigValue parse(ConstantPool pool, DynByteBuf r) {
		int type = r.readUnsignedByte();
		switch (type) {
			case BOOLEAN, BYTE, SHORT, CHAR, INT:
			case DOUBLE, FLOAT, LONG, STRING, ANNOTATION_CLASS:
				Constant c = pool.resolve(r);
				return switch (type) {
					case BOOLEAN -> valueOf(((CstInt) c).value != 0);
					case BYTE -> valueOf((byte) ((CstInt) c).value);
					case SHORT -> valueOf((short) ((CstInt) c).value);
					case CHAR -> valueOf((char) ((CstInt) c).value);
					case INT -> valueOf(((CstInt) c).value);
					case LONG -> valueOf(((CstLong) c).value);
					case FLOAT -> valueOf(((CstFloat) c).value);
					case DOUBLE -> valueOf(((CstDouble) c).value);
					case STRING -> valueOf(((CstUTF) c).str());
					case ANNOTATION_CLASS -> valueOf(Type.getType(((CstUTF) c).str()));
					default -> throw new IllegalStateException("Unexpected value: " + type);
				};
			case ENUM: return new EnumVal(checkSemicolon(((CstUTF) pool.resolve(r)).str()), ((CstUTF) pool.resolve(r)).str());
			case ANNOTATION: return Annotation.parse(pool, r);
			case ARRAY:
				int len = r.readUnsignedShort();
				List<ConfigValue> annos = new ArrayList<>(len);
				while (len-- > 0) annos.add(parse(pool, r));
				return new ArrayVal(annos);
		}
		throw new IllegalArgumentException("Unknown annotation value type '"+(char) type+"'");
	}

	static String checkSemicolon(String str) {
		if (!str.endsWith(";")) throw new IllegalArgumentException("无效的类型(要求描述符而不是类名):"+str);
		return str;
	}

	@Override public final roj.config.node.Type getType() {return roj.config.node.Type.OTHER;}
	@Override public abstract char dataType();
}