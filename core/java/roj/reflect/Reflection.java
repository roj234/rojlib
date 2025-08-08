package roj.reflect;

import org.jetbrains.annotations.ApiStatus;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.asm.type.Type;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class Reflection {
	public static final int JAVA_VERSION;
	public static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

	static final Unsafe u;
	@ReferenceByGeneratedClass
	public static final MethodHandles.Lookup IMPL_LOOKUP;

	@ReferenceByGeneratedClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟";
	private static final BiConsumer<Object, String> openModule;
	private static final Function<Object[], Class<?>> defineClass;

	static {
		// better solution, but not available in 8
		//JAVA_VERSION = Runtime.version().major();

		String v = System.getProperty("java.specification.version");
		// 事实上我们只关心它是否大于8，所以其实不是那么必要/doge
		if (v.startsWith("1.")) v = v.substring(2);
		JAVA_VERSION = Integer.parseInt(v);

		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			u = (Unsafe) f.get(null);

			byte[] bytes = readExact("roj/reflect/Reflection$.class", 1645);

			Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			IMPL_LOOKUP = (MethodHandles.Lookup) u.getObject(u.staticFieldBase(implLookup), u.staticFieldOffset(implLookup));

			if (JAVA_VERSION > 21) {
				bytes[175] = 0x3F;
				bytes[1324] = 0x3F;
			}
			var type = IMPL_LOOKUP.defineClass(bytes);

			if (JAVA_VERSION > 21) IMPL_LOOKUP.ensureInitialized(type);
			else u.ensureClassInitialized(type);

			Object instance = System.getProperties().remove(PROP_NAME);
			defineClass = Helpers.cast(instance);
			openModule = Helpers.cast(instance);

			if (JAVA_VERSION > 21) {
				killJigsaw(Reflection.class);
				System.err.println("[RojLib Warning] Java22+兼容模式：模块系统已为RojLib模块禁用，这会造成严重的安全问题，请确保为RojLib使用了独立的模块！");
			}
		} catch (Exception e) {
			String msg = JAVA_VERSION < 8 || JAVA_VERSION > 21 ? "您使用的JVM版本"+JAVA_VERSION+"不受支持!" : "未预料的内部错误";
			throw new InternalError(msg, e);
		}
	}
	static byte[] readExact(String resource, int count) throws IOException {
		byte[] b = new byte[count];
		int off = 0;
		try (var in = Reflection.class.getClassLoader().getResourceAsStream(resource)) {
			if (in != null) {
				while (off < b.length) {
					int r = in.read(b, off, b.length - off);
					if (r < 0) throw new AssertionError("数据错误");
					off += r;
				}
				if (in.read() < 0) return b;
			}
		}
		throw new AssertionError("数据错误");
	}

	public static final String MAGIC_ACCESSOR_CLASS = JAVA_VERSION > 21 ? "java/lang/Object" : CLASS_NAME;
	public static Class<?> DefineWeakClass(String displayName, byte[] b) {
		/*
		 * Flags for Lookup.ClassOptions
		 */
		return defineClass.apply(new Object[]{Reflection.class.getClassLoader(), Reflection.class, displayName, b, 0, b.length, Reflection.class.getProtectionDomain(), false, 2/*HIDDEN_CLASS*/|8/*ACCESS_VM_ANNOTATIONS*/, null});
	}

	@ApiStatus.Internal
	public static Field getField(Class<?> type, String name) throws NoSuchFieldException {
		while (type != null && type != Object.class) {
			try {
				Field field = type.getDeclaredField(name);

				var sm = ILSecurityManager.getSecurityManager();
				if (sm != null && !sm.checkAccess(field, getCallerClass(2, Unaligned.class))) break;

				return field;
			} catch (NoSuchFieldException ignored) {}
			type = type.getSuperclass();
		}
		throw new NoSuchFieldException(name);
	}

	public static String capitalizedType(Type type) {return type.isPrimitive()?upper(type.toString()):"Object";}
	public static String upper(String s) {
		CharList sb = new CharList(s);
		sb.set(0, Character.toUpperCase(sb.charAt(0)));
		return sb.toStringAndFree();
	}

	public static void ensureClassInitialized(Class<?> klass) { U.ensureClassInitialized(klass); }

	/**
	 * 在Class及其实例被GC时，自动从VM中卸载这个类
	 */
	public static Class<?> defineWeakClass(ByteList b) {
		if (Debug.CLASS_DUMP) Debug.dump("weak", b);
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) b = sm.preDefineClass(b);
		return DefineWeakClass(null, b.toByteArray());
	}

	/**
	 * 对target_module开放src_module中的src_package
	 * src_module和target_module的类型是[Class|Module]
	 */
	private static void openModule(Class<?> src_module, String src_package, Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkOpenModule(src_module, src_package, target_module);

		openModule.accept(new Object[] {src_module, target_module}, src_package);
	}
	/**
	 * 禁用模块权限系统
	 */
	public static void killJigsaw(Class<?> target_module) {
		var sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkKillJigsaw(target_module);

		for (Module module : Object.class.getModule().getLayer().modules()) {
			for (String pkg : module.getDescriptor().packages()) {
				openModule.accept(new Object[] {module, target_module}, pkg);
			}
		}
	}

	public static Class<?> getCallerClass(int backward) {return getCallerClass(backward+1, null);}
	private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 5);
	public static Class<?> getCallerClass(int backward, Class<?> skipIf) {
		return WALKER.walk(stream -> {
			var frame = stream.skip(backward).filter(frame1 -> frame1.getDeclaringClass() != skipIf).findFirst().orElse(null);
			return frame == null ? null : frame.getDeclaringClass();
		});
	}

	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	public static int uniqueId() { return NEXT_ID.getAndIncrement(); }
}