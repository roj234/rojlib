package roj.reflect;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.Helpers;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static roj.asm.type.Type.ARRAY;
import static roj.asm.type.Type.CLASS;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class ReflectionUtils {
	public static final Unsafe u = getUnsafe();
	private static Unsafe getUnsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe) f.get(null);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	public static long fieldOffset(Class<?> type, String fieldName) {
		try {
			Field field = type.getDeclaredField(fieldName);
			ILSecurityManager sm = ILSecurityManager.getSecurityManager();
			if (sm != null && !sm.checkAccess(field)) throw new SecurityException("access denied");
			return (field.getModifiers() & AccessFlag.STATIC) == 0 ? u.objectFieldOffset(field) : u.staticFieldOffset(field);
		} catch (Exception e) {
			Helpers.athrow(e);
			return 0;
		}
	}

	public static final int JAVA_VERSION;
	static {
		String v = System.getProperty("java.version");
		String major = v.substring(0, v.indexOf('.'));
		JAVA_VERSION = Integer.parseInt(major);
	}

	/**
	 * 获取current + 父类 所有Field
	 */
	public static List<Field> getFields(Class<?> clazz) {
		SimpleList<Field> fields = new SimpleList<>();
		while (clazz != null && clazz != Object.class) {
			fields.addAll(clazz.getDeclaredFields());
			clazz = clazz.getSuperclass();
		}
		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.filterFields(fields);
		return fields;
	}

	public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
		while (clazz != null && clazz != Object.class) {
			try {
				Field field = clazz.getDeclaredField(name);

				ILSecurityManager sm = ILSecurityManager.getSecurityManager();
				if (sm == null || sm.checkAccess(field)) {
					field.setAccessible(true);
					return field;
				}
			} catch (NoSuchFieldException ignored) {}
			clazz = clazz.getSuperclass();
		}
		throw new NoSuchFieldException(name);
	}

	/**
	 * 获取current + 父类 所有Method
	 */
	public static List<Method> getMethods(Class<?> clazz) {
		MyHashSet<Method> methods = new MyHashSet<>();
		while (clazz != null && clazz != Object.class) {
			methods.addAll(clazz.getDeclaredMethods());
			clazz = clazz.getSuperclass();
		}
		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.filterMethods(methods);
		return new SimpleList<>(methods);
	}

	public static Field checkFieldName(Class<?> type, String... names) throws NoSuchFieldException {
		MyHashSet<String> set = new MyHashSet<>(names);
		for (Field field : type.getDeclaredFields()) {
			if (set.contains(field.getName())) return field;
		}
		throw new NoSuchFieldException(type.getName()+"中没有任一字段匹配提供的名称"+set);
	}

	public static FieldAccessor access(@Nonnull Field field) { return new FieldAccessor(field); }

	public static List<Class<?>> getAllParentsWithSelfOrdered(Class<?> clazz) {
		SimpleList<Class<?>> classes = new SimpleList<>();
		SimpleList<Class<?>> pending = new SimpleList<>();
		pending.add(clazz);

		while (!pending.isEmpty()) {
			int size = pending.size();
			for (int i = 0; i < size; i++) {
				Class<?> c = pending.get(i);
				if (!classes.contains(c)) {
					classes.add(c);

					Collections.addAll(pending, c.getInterfaces());
					Class<?> s = c.getSuperclass();
					if (s != Object.class && s != null) pending.add(s);
				}
			}
			pending.removeRange(0, size);
		}
		return classes;
	}

	public static String accessorName(Field field) {
		char c = TypeHelper.class2asm(field.getType()).charAt(0);
		switch (c) {
			case ARRAY: case CLASS: return "Object";
			default:
				StringBuilder s = new StringBuilder(Type.toString((byte) c));
				s.setCharAt(0, Character.toUpperCase(s.charAt(0)));
				return s.toString();
		}
	}
}