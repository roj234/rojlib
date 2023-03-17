package roj.asm.misc;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.tree.FieldNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.util.DynByteBuf;

import java.lang.reflect.Field;

/**
 * Java Reflection Field.MoFNode
 *
 * @author Roj233
 * @since 2022/1/11 2:13
 */
public final class ReflectFNode implements FieldNode {
	private final Field field;

	public ReflectFNode(Field field) {this.field = field;}

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String name() {
		return field.getName();
	}

	@Override
	public String rawDesc() {
		return TypeHelper.class2asm(field.getType());
	}

	@Override
	public char accessFlag() {
		return (char) field.getModifiers();
	}

	@Override
	public int type() {
		return Parser.FTYPE_REFLECT;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ReflectFNode that = (ReflectFNode) o;

		return field.getName().equals(that.field.getName());
	}

	@Override
	public int hashCode() {
		return field.hashCode();
	}

	@Override
	public Type fieldType() {
		return TypeHelper.parseField(rawDesc());
	}
}
