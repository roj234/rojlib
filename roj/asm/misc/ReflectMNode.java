package roj.asm.misc;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.util.DynByteBuf;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Java Reflection Method.MoFNode
 *
 * @author Roj233
 * @since 2021/9/27 13:49
 */
public final class ReflectMNode implements MethodNode {
	private final Method method;
	private String desc;

	public ReflectMNode(Method method) {this.method = method;}

	@Override
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String name() {
		return method.getName();
	}

	@Override
	public String rawDesc() {
		if (desc != null) return desc;
		return desc = TypeHelper.class2asm(method.getParameterTypes(), method.getReturnType());
	}

	@Override
	public char accessFlag() {
		return (char) method.getModifiers();
	}

	@Override
	public int type() {
		return Parser.MTYPE_REFLECT;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ReflectMNode that = (ReflectMNode) o;

		return method.getName().equals(that.method.getName()) && Arrays.equals(method.getParameterTypes(), that.method.getParameterTypes());
	}

	@Override
	public int hashCode() {
		return method.hashCode();
	}

	@Override
	public String ownerClass() {
		return method.getDeclaringClass().getName().replace('.', '/');
	}

	@Override
	public List<Type> parameters() {
		List<Type> types = TypeHelper.parseMethod(rawDesc());
		types.remove(types.size() - 1);
		return types;
	}

	@Override
	public Type getReturnType() {
		return TypeHelper.parseReturn(rawDesc());
	}
}
