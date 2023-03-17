package roj.asm.misc;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2021/9/29 12:58
 */
public class ReflectClass implements IClass {
	public final Class<?> owner;
	public final String className;
	private List<MoFNode> methods, fields;
	private List<String> ci, cs;
	private List<Class<?>> list1;

	public static ReflectClass from(Class<?> clazz) {
		return new ReflectClass(clazz);
	}

	public ReflectClass(Class<?> owner) {
		this.owner = owner;
		this.className = owner.getName().replace('.', '/');
	}

	@Deprecated
	public ReflectClass(String owner, List<MoFNode> methods) {
		this.owner = null;
		this.className = owner;
		this.methods = methods;
		this.fields = Collections.emptyList();
	}

	@Override
	public String name() {
		return className;
	}

	@Override
	public String parent() {
		return owner.getSuperclass().getName().replace('.', '/');
	}

	@Override
	public char accessFlag() {
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
	public List<? extends MoFNode> methods() {
		if (methods == null) {
			Method[] ms = owner.getDeclaredMethods();
			List<MoFNode> md = methods = Arrays.asList(new ReflectMNode[ms.length]);
			for (int i = 0; i < ms.length; i++) {
				md.set(i, new ReflectMNode(ms[i]));
			}
		}
		return methods;
	}

	@Override
	public List<? extends MoFNode> fields() {
		if (fields == null) {
			Field[] fs = owner.getDeclaredFields();
			List<MoFNode> fd = fields = Arrays.asList(new ReflectFNode[fs.length]);
			for (int i = 0; i < fs.length; i++) {
				fd.set(i, new ReflectFNode(fs[i]));
			}
		}
		return fields;
	}

	@Override
	public int type() {
		return Parser.CTYPE_REFLECT;
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

	public ConstantData unreflected() {
		try {
			String cn = owner.getName().replace('.', '/').concat(".class");
			InputStream in = owner.getClassLoader().getResourceAsStream(cn);
			if (in == null) return null;
			return Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
