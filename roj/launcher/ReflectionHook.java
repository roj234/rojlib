package roj.launcher;

import roj.asm.type.TypeHelper;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.mapper.MapUtil;
import roj.mapper.util.Desc;
import roj.text.logging.Logger;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since 2023/8/4 0004 15:36
 */
public class ReflectionHook extends MethodHook {
	public MyHashMap<Desc, Desc> bannedMethod;
	public static WeakHashMap<Object, String> memberNames = new WeakHashMap<>();

	private static final Method INVOKE, NEWINSTANCE;
	static {
		try {
			INVOKE = Method.class.getDeclaredMethod("invoke", Object.class, Object[].class);
			NEWINSTANCE = Constructor.class.getDeclaredMethod("newInstance", Object[].class);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Logger LOGGER = Logger.getLogger("Reflection Banner");

	// region stub code
	@RealDesc(value = "java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;", callFrom = true)
	public static Class<?> hook_static_forName(String name, Class<?> caller) throws Exception {
		return checkClass(name, true, caller.getClassLoader());
	}
	@RealDesc("java/lang/Class.forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")
	public static Class<?> hook_static_forName(String name, boolean init, ClassLoader loader) throws Exception { return checkClass(name, init, loader); }

	@RealDesc({
		"java/lang/reflect/Field.getName()Ljava/lang/String;",
		"java/lang/reflect/Method.getName()Ljava/lang/String;",
		"java/lang/reflect/Constructor.getName()Ljava/lang/String;",
		"java/lang/reflect/Executable.getName()Ljava/lang/String;",
		"java/lang/reflect/Member.getName()Ljava/lang/String;",
	})
	public static String hook_getName(Member m) { return memberNames.getOrDefault(m, m.getName()); }
	public static String hook_getName(Class<?> c) { return memberNames.getOrDefault(c, c.getName()); }

	private static final Method[] EMPTY_METHODS = new Method[0];
	private static final Field[] EMPTY_FIELDS = new Field[0];
	private static final Constructor<?>[] EMPTY_CONSTRUCTORS = new Constructor<?>[0];

	public static Method hook_getMethod(Class<?> c, String name, Class<?>[] param) throws Exception { return filterMethod(c.getMethod(name, param), true); }
	public static Method hook_getDeclaredMethod(Class<?> c, String name, Class<?>[] param) throws Exception { return filterMethod(c.getDeclaredMethod(name, param), true); }
	public static Method[] hook_getMethods(Class<?> c) { return filterMethods(c.getMethods()); }
	public static Method[] hook_getDeclaredMethods(Class<?> c) { return filterMethods(c.getDeclaredMethods()); }
	private static Method[] filterMethods(Method[] a) {
		SimpleList<Method> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Method f = filterMethod(list.get(i), false);
			if (f == null) {
				list.remove(i);
				a = EMPTY_METHODS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Method filterMethod(Method m, boolean _throw) {
		Desc d = MapUtil.getInstance().sharedDC;
		d.owner = m.getDeclaringClass().getName().replace('.', '/');
		d.name = m.getName();
		d.param = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());

		Object v = ban(d);
		if (v == null) return m;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchMethodException(m.getDeclaringClass().getName()+'.'+m.getName()+argumentTypesToString(m.getParameterTypes())));
			return null;
		}
		return (Method) v;
	}

	public static Object hook_invoke(Method m, Object inst, Object[] value) throws Exception {
		while (m.equals(INVOKE)) {
			m = (Method) inst;
			inst = value[0];
			value = (Object[]) value[1];
		}

		if (m.equals(NEWINSTANCE)) return hook_newInstance((Constructor<?>) inst, value);


		return checkInvoke(m, inst, value);
	}

	public static Field hook_getField(Class<?> c, String name) throws Exception { return filterField(c.getField(name), true); }
	public static Field hook_getDeclaredField(Class<?> c, String name) throws Exception { return filterField(c.getDeclaredField(name), true); }
	public static Field[] hook_getFields(Class<?> c) { return filterFields(c.getFields()); }
	public static Field[] hook_getDeclaredFields(Class<?> c) { return filterFields(c.getDeclaredFields()); }
	private static Field[] filterFields(Field[] a) {
		SimpleList<Field> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Field f = filterField(list.get(i), false);
			if (f == null) {
				list.remove(i);
				a = EMPTY_FIELDS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Field filterField(Field f, boolean _throw) {
		Desc d = MapUtil.getInstance().sharedDC;
		d.owner = f.getDeclaringClass().getName().replace('.', '/');
		d.name = f.getName();
		d.param = TypeHelper.class2asm(f.getType());

		Object v = ban(d);
		if (v == null) return f;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchFieldException(f.getName()));
			return null;
		}
		return (Field) v;
	}

	public static Constructor<?> hook_getConstructor(Class<?> c, Class<?>[] param) throws Exception { return filterConstructor(c.getConstructor(param), true); }
	public static Constructor<?> hook_getDeclaredConstructor(Class<?> c, Class<?>[] param) throws Exception { return filterConstructor(c.getDeclaredConstructor(param), true); }
	public static Constructor<?>[] hook_getConstructors(Class<?> c) { return filterConstructors(c.getConstructors()); }
	public static Constructor<?>[] hook_getDeclaredConstructors(Class<?> c) { return filterConstructors(c.getDeclaredConstructors()); }
	private static Constructor<?>[] filterConstructors(Constructor<?>[] a) {
		SimpleList<Constructor<?>> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Constructor<?> f = filterConstructor(list.get(i), false);
			if (f == null) {
				list.remove(i);
				a = EMPTY_CONSTRUCTORS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Constructor<?> filterConstructor(Constructor<?> c, boolean _throw) {
		Desc d = MapUtil.getInstance().sharedDC;
		d.owner = c.getDeclaringClass().getName().replace('.', '/');
		d.name = "<init>";
		d.param = TypeHelper.class2asm(c.getParameterTypes(), void.class);

		Object v = ban(d);
		if (v == null) return c;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchMethodException(c.getDeclaringClass().getName()+".<init>"+argumentTypesToString(c.getParameterTypes())));
			return null;
		}
		return (Constructor<?>) v;
	}

	public static Object hook_newInstance(Class<?> c) throws Exception {
		Desc d = MapUtil.getInstance().sharedDC;
		d.owner = c.getDeclaringClass().getName().replace('.', '/');
		d.name = "<init>";
		d.param = "()V";

		Object v = ban(d);
		if (v == null) return c.newInstance();
		if (v == IntMap.UNDEFINED) throw new InstantiationException(c.getName());
		return ((Constructor<?>) v).newInstance(ArrayCache.OBJECTS);
	}

	private static String argumentTypesToString(Class<?>[] types) {
		StringBuilder buf = new StringBuilder().append('(');
		for (int i = 0; i < types.length; i++) {
			if (i > 0) buf.append(", ");

			Class<?> c = types[i];
			buf.append((c == null) ? "null" : c.getName());
		}
		return buf.append(')').toString();
	}
	// endregion

	private static Object checkInvoke(Method m, Object inst, Object[] value) throws Exception {
		LOGGER.info("Invoke {} with param {}", m, value);
		return m.invoke(inst, value);
	}

	private static Class<?> checkClass(String name, boolean init, ClassLoader loader) throws Exception {
		LOGGER.info("Load {} with classLoader {}", name, loader);
		return Class.forName(name, init, loader);
	}

	public static Object hook_newInstance(Constructor<?> c, Object[] value) throws Exception {
		return c.newInstance(value);
	}

	private static Object ban(Desc d) {
		if (d.owner.equals("java/lang/reflect/Constructor") || d.owner.equals("java/lang/reflect/Method")) {
			// prevent getting internal fields
			if (d.param.indexOf('(') == -1) return IntMap.UNDEFINED;
		}

		LOGGER.info("Check {}", d);
		return null;
	}

	public void banMethod(Desc desc) {

	}
}
