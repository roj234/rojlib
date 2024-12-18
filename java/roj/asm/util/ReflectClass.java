package roj.asm.util;

import roj.asm.Parser;
import roj.asm.tree.*;
import roj.reflect.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj233
 * @since 2021/9/29 12:58
 */
@Deprecated
public class ReflectClass implements IClass {
	public final Class<?> owner;
	public final String className;
	private List<RawNode> methods, fields;
	private List<String> ci, cs;
	private List<Class<?>> list1;

	public static ReflectClass from(Class<?> clazz) { return new ReflectClass(clazz); }
	public ReflectClass(Class<?> owner) {
		this.owner = owner;
		this.className = owner.getName().replace('.', '/');
	}

	@Override
	public String name() { return className; }

	@Override
	public String parent() {
		Class<?> parent = owner.getSuperclass();
		return parent == null ? null : parent.getName().replace('.', '/');
	}

	@Override
	public char modifier() {
		return owner == null ? 0 : (char) owner.getModifiers();
	}

	@Override
	public List<String> interfaces() {
		if (ci != null) return ci;
		Class<?>[] itf = owner.getInterfaces();
		List<String> arr = Arrays.asList(new String[itf.length]);
		for (int i = 0; i < itf.length; i++) {
			arr.set(i, itf[i].getName().replace('.', '/'));
		}
		return ci = arr;
	}

	@Override
	public List<? extends RawNode> methods() {
		if (methods == null) {
			Method[] ms = owner.getDeclaredMethods();
			List<RawNode> md = methods = Arrays.asList(new MethodNode[ms.length]);
			for (int i = 0; i < ms.length; i++) {
				md.set(i, new MethodNode(ms[i]));
			}
		}
		return methods;
	}

	@Override
	public List<? extends RawNode> fields() {
		if (fields == null) {
			Field[] fs = owner.getDeclaredFields();
			List<RawNode> fd = fields = Arrays.asList(new FieldNode[fs.length]);
			for (int i = 0; i < fs.length; i++) {
				fd.set(i, new FieldNode(fs[i]));
			}
		}
		return fields;
	}

	public List<String> i_superClassAll() {
		if (cs != null) return cs;

		List<Class<?>> t = allParentsWithSelf();
		List<String> names = Arrays.asList(new String[t.size()-1]);
		for (int i = 1; i < t.size(); i++) {
			names.set(i-1, t.get(i).getName().replace('.', '/'));
		}
		return cs = names;
	}

	public List<Class<?>> allParentsWithSelf() {
		if (list1 == null) list1 = ReflectionUtils.getAllParentsWithSelfOrdered(owner);
		return list1;
	}

	public ConstantData unreflected() { return Parser.parseConstants(owner); }

	@Override
	public boolean equals(Object obj) { return obj instanceof ReflectClass && ((ReflectClass) obj).owner == owner; }
	@Override
	public int hashCode() { return owner.hashCode(); }
}