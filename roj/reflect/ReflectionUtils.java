package roj.reflect;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static roj.asm.type.Type.ARRAY;
import static roj.asm.type.Type.CLASS;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class ReflectionUtils {
	public static boolean OPENJ9;

	static {
		try {
			Class.forName("java.lang.J9VMInternals");
			OPENJ9 = true;
		} catch (ClassNotFoundException e) {
			OPENJ9 = false;
		}

		/*if (getJavaVersion() > 8) {
			try (InputStream in = ReflectionUtils.class.getResourceAsStream("roj/reflect/FieldAccessor.class")) {
				ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in));
				for (Constant c : data.cp.array()) {
					if (c.type() == Constant.CLASS) {
						CstClass clz = (CstClass) c;
						if (clz.getValue().getString().equals("sum/misc/Unsafe")) {
							clz.getValue().setString(unsafeName);
							break;
						}
					}
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to read jvav code for UFA", e);
			}
		}*/
	}

	public static final int JAVA_VERSION;
	static {
		String v = System.getProperty("java.version");
		String major = v.substring(0, v.indexOf('.'));
		JAVA_VERSION = Integer.parseInt(major);
	}

	/**
	 * 在obj中查找类型为targetClass的field
	 *
	 * @param clazz The class to find field
	 * @param fieldClass The field class to find
	 */
	public static Field getFieldByType(Class<?> clazz, Class<?> fieldClass) {
		for (Field f : getFields(clazz)) {
			Class<?> tmp = f.getType();
			while (tmp != Object.class && tmp != null) {
				if (tmp == fieldClass) {
					f.setAccessible(true);
					return f;
				}
				tmp = tmp.getSuperclass();
			}
		}
		return null;
	}

	/**
	 * 获取current + 父类 所有Field
	 */
	public static List<Field> getFields(Class<?> clazz) {
		SimpleList<Field> fields = new SimpleList<>();
		while (clazz != null && clazz != Object.class) {
			Collections.addAll(fields, clazz.getDeclaredFields());
			clazz = clazz.getSuperclass();
		}
		return fields;
	}

	public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
		while (clazz != null && clazz != Object.class) {
			try {
				Field field = clazz.getDeclaredField(name);
				field.setAccessible(true);
				return field;
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
		return new SimpleList<>(methods);
	}

	/**
	 * 获取字段值
	 *
	 * @param instance 实例
	 * @param clazz 实例类
	 * @param name name
	 *
	 * @return value
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValue(Object instance, Class<?> clazz, String name) {
		try {
			return (T) getField(clazz, name).get(instance);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 获取本类字段值
	 *
	 * @param instance 实例
	 * @param name name
	 *
	 * @return value
	 */
	public static <T> T getValue(Object instance, String name) {
		return getValue(instance, instance.getClass(), name);
	}

	/**
	 * 设置字段值
	 *
	 * @param instance 实例
	 * @param name name
	 * @param value new value
	 */
	public static void setValue(Object instance, @Nonnull Class<?> clazz, String name, Object value) throws NoSuchFieldException, IllegalAccessException {
		getField(clazz, name).set(instance, value);
	}

	/**
	 * 设置static final字段值
	 *
	 * @param clazz class
	 * @param fieldName name
	 * @param value new value
	 */
	public static void setFinal(Class<?> clazz, String fieldName, Object value) throws NoSuchFieldException {
		setFinal(null, clazz, fieldName, value);
	}

	/**
	 * 设置final字段值
	 *
	 * @param o instance of this
	 * @param fieldName name
	 * @param value new value
	 */
	public static void setFinal(@Nonnull Object o, String fieldName, Object value) throws NoSuchFieldException {
		setFinal(o, getField(o.getClass(), fieldName), value);
	}

	/**
	 * 设置final字段值
	 *
	 * @param o instance
	 * @param clazz class
	 * @param fieldName name
	 * @param value new value
	 */
	public static void setFinal(Object o, Class<?> clazz, String fieldName, Object value) throws NoSuchFieldException {
		setFinal(o, getField(clazz, fieldName), value);
	}

	/**
	 * 设置final字段值
	 *
	 * @param field The field
	 * @param value new value
	 */
	public static void setFinal(Field field, Object value) {
		setFinal(null, field, value);
	}

	public static void setFinal(Object o, Field field, Object value) {
		access(field).setObject(o, value);
	}

	public static FieldAccessor access(@Nonnull Field field) {
		return new FieldAccessor(field);
	}

	public static List<Class<?>> getFathers(Object target) {
		return getFathers(target instanceof Class ? (Class<?>) target : target.getClass());
	}

	public static List<Class<?>> getFathers(Class<?> clazz) {
		List<Class<?>> classes = new ArrayList<>();
		while (clazz != Object.class && clazz != null) {
			classes.add(clazz);
			Collections.addAll(classes, clazz.getInterfaces());
			clazz = clazz.getSuperclass();
		}
		return classes;
	}

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
			case ARRAY:
			case CLASS:
				return "Object";
			default:
				StringBuilder s = new StringBuilder(Type.toString((byte) c));
				s.setCharAt(0, Character.toUpperCase(s.charAt(0)));
				return s.toString();
		}
	}
}
