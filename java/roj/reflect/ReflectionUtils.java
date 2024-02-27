package roj.reflect;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.util.ByteList;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.type.Type.ARRAY;
import static roj.asm.type.Type.CLASS;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class ReflectionUtils {
	public static final int JAVA_VERSION = VMInternals.JAVA_VERSION;

	public static final Unsafe u = VMInternals.u;
	public static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

	public static long fieldOffset(Class<?> type, String fieldName) {
		try {
			var field = getField(type, fieldName);
			return (field.getModifiers() & Opcodes.ACC_STATIC) == 0 ? u.objectFieldOffset(field) : u.staticFieldOffset(field);
		} catch (Exception e) {
			Helpers.athrow(e);
			return 0;
		}
	}

	@Nullable
	public static Field getFieldIfMatch(Class<?> type, String fieldType) {
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				if (field.getType().getName().endsWith(fieldType)) {
					var sm = ILSecurityManager.getSecurityManager();
					return sm == null || sm.checkAccess(field) ? field : null;
				}
			}
			type = type.getSuperclass();
		}
		return null;
	}

	public static Field getField(Class<?> type, String name) throws NoSuchFieldException {
		while (type != null && type != Object.class) {
			try {
				Field field = type.getDeclaredField(name);

				var sm = ILSecurityManager.getSecurityManager();
				if (sm == null || sm.checkAccess(field)) {
					//field.setAccessible(true);
					return field;
				}
			} catch (NoSuchFieldException ignored) {}
			type = type.getSuperclass();
		}
		throw new NoSuchFieldException(name);
	}

	public static Field[] getFieldsByName(Class<?> type, String[] names) {
		Field[] out = new Field[names.length];
		ToIntMap<String> map = ToIntMap.fromArray(names);
		var sm = ILSecurityManager.getSecurityManager();
		var origClass = type;

		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				int i = map.removeInt(field.getName());
				if (i >= 0) {
					if (sm != null && !sm.checkAccess(field)) break;

					out[i] = field;
					if (map.isEmpty()) return out;
				}
			}

			type = type.getSuperclass();
		}

		throw new IllegalStateException("未找到某些字段:"+origClass+"."+map.keySet());
	}

	/**
	 * 获取current + 父类 所有Method
	 */
	public static List<Method> getMethods(Class<?> clazz, String... methodNames) {
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

	@Deprecated
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

	/**
	 * 在Class及其实例被GC时，自动从VM中卸载这个类
	 */
	public static Class<?> defineWeakClass(ByteList b) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) b = sm.checkDefineClass(null, b);
		return VMInternals.DefineWeakClass(null, b.toByteArray());
	}
	public static void ensureClassInitialized(Class<?> klass) { VMInternals.InitializeClass(klass); }
	/**
	 * 对target_module开放src_module中的src_package
	 */
	public static void openModule(Class<?> src_module, String src_package, Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkOpenModule(src_module, src_package, target_module);

		VMInternals.OpenModule(src_module, src_package, target_module);
	}
	/**
	 * 禁用模块权限系统
	 */
	public static void killJigsaw(Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkKillJigsaw(target_module);

		for (Module module : Object.class.getModule().getLayer().modules()) {
			for (String pkg : module.getDescriptor().packages()) {
				VMInternals.OpenModule(module, pkg, target_module.getModule());
			}
		}
	}

	public static Class<?> getCallerClass(int backward) {return JAVA_VERSION < 17 ? Tracer.INSTANCE.getCallerClass(backward+1) : GetCallerArgs.getCallerClass(backward+1);}
	private static final class Tracer extends SecurityManager {
		// avoid security manager creation warning
		static final Tracer INSTANCE = new Tracer();

		public Class<?> getCallerClass(int backward) {
			Class<?>[] ctx = super.getClassContext();
			return ctx.length < backward ? null : ctx[backward];
		}
	}

	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	public static int uniqueId() { return NEXT_ID.getAndIncrement(); }
}