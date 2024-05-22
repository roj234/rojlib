package roj.reflect;

import roj.ReferenceByGeneratedClass;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.compiler.asmlang.ASM.__asm;

/**
 * 在"不归路"上越走越远
 * @author Roj234
 * @since 2023/2/11 14:16
 */
final class VMInternals {
	@ReferenceByGeneratedClass
	private static final String PROP_NAME = "_ILJ9DC_", CLASS_NAME = "java/lang/🔓_IL🐟"; // "海阔凭鱼跃，天高任鸟飞"

	static final int JAVA_VERSION;
	static final Unsafe u;

	static {
		String v = System.getProperty("java.version");
		String major = v.substring(0, v.indexOf('.'));
		JAVA_VERSION = Integer.parseInt(major);

		Unsafe uu;
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			uu = (Unsafe) f.get(null);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			catchException(e);
			uu = null;
		}
		u = uu;

		if (JAVA_VERSION > 8) {
			try (DataInputStream in = new DataInputStream(VMInternals.class.getClassLoader().getResourceAsStream("META-INF/ReflectionCompat.class"))) {
				if (JAVA_VERSION >= 17) {
					// 失效后解法，防止我忘了：Unsafe遍历找allowedMode替换
					// 如果Unsafe也不让拿：
					// 根据 https://github.com/xxDark/Venuzdonoa 拿到 ClassLoader::defineClass2 去加载VMHook
					Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
					_ImplLookup = (MethodHandles.Lookup) uu.getObject(uu.staticFieldBase(implLookup), uu.staticFieldOffset(implLookup));
				}

				defineClass(readClassData(in));
			} catch (Exception e) {
				catchException(e);
			}
		}
	}

	private static byte[] readClassData(DataInputStream in) throws IOException {
		byte[] b = new byte[2048];
		int off = 0;
		while (off < b.length) {
			int r = in.read(b, off, b.length - off);
			if (r < 0) {
				assert off == 1645 : "class size not match original";
				return Arrays.copyOf(b, off);
			}
			off += r;
		}
		return b;
	}

	private static MethodHandles.Lookup _ImplLookup;
	private static String _MagicAccessor;
	private static BiConsumer<Object, String> _ModuleOpener;
	private static Function<Object[], Class<?>> _ClassDefiner;

	private static void defineClass(byte[] bytes) {
		Class<?> type;
		try {
			if (JAVA_VERSION < 17) {
				type = DefineWeakClass(bytes);
			} else {
				type = _ImplLookup.defineClass(bytes);
			}
		} catch (Exception e) {
			catchException(e);
			return;
		}
		InitializeClass(type);

		Object o = System.getProperties().remove(PROP_NAME);
		_MagicAccessor = type.getName().replace('.', '/');
		_ClassDefiner = Helpers.cast(o);
		_ModuleOpener = Helpers.cast(o);
	}

	/**
	 * Flags for Lookup.ClassOptions
	 */
	static final int
		HIDDEN_CLASS              = 0x00000002,
		ACCESS_VM_ANNOTATIONS     = 0x00000008;

	// not removed still in java 21, but merge to one place for future refactor
	static void InitializeClass(Class<?> klass) { u.ensureClassInitialized(klass); }
	static String HackMagicAccessor() { return JAVA_VERSION <= 8 ? "sun/reflect/MagicAccessorImpl" : _MagicAccessor; }
	static void OpenModule(Class<?> src_module, String src_package, Class<?> target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static void OpenModule(Module src_module, String src_package, Module target_module) { _ModuleOpener.accept(new Object[] {src_module, target_module}, src_package); }
	static Class<?> DefineWeakClass(byte[] b) {
		if (JAVA_VERSION < 17) {
			if (__asm("""
				getstatic this u
				ldc sun.misc.Unsafe.class
				aload 1
				aconst_null
				invokevirtual java/lang/Unsafe defineAnonymousClass (Ljava/lang/Class;[B[Ljava/lang/Object;)Ljava/lang/Class;
				areturn
				""")) {
				try {
					Method m = Unsafe.class.getDeclaredMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
					m.setAccessible(true);
					return (Class<?>) m.invoke(u, Unsafe.class, b, null);
				} catch (Exception e) {
					catchException(e);
				}
			}
		}
		return _ClassDefiner.apply(new Object[]{VMInternals.class.getClassLoader(), VMInternals.class, null, b, 0, b.length, VMInternals.class.getProtectionDomain(), false, HIDDEN_CLASS, null});
	}
	// 卡拉赞 (所以没意义)
	// 嗯啊，其实还是有意义的 -> boot class loader only的注解 比如CallerSensitive ForceInline什么的
	// 但是编译太麻烦
	static Class<?> DefineVMClass(String name, byte[] b, int off, int len) {
		block:
		if (JAVA_VERSION < 17) {
			if (__asm("""
				getstatic this u
				aload 1
				aload 2
				iload 3
				iload 4
				aconst_null
				aconst_null
				invokevirtual java/lang/Unsafe defineClass (Ljava/lang/String;[BIILjava/lang/ClassLoader;Ljava/security/ProtectionDomain;)Ljava/lang/Class;
				areturn
				""")) {
				Method m;
				try {
					m = Unsafe.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
				} catch (NoSuchMethodException e) {
					break block;
				}

				try {
					m.setAccessible(true);
					return (Class<?>) m.invoke(u, name, b, off, len, null, null);
				} catch (Exception e) {
					catchException(e);
				}
			}
		}

		return _ClassDefiner.apply(new Object[]{null, VMInternals.class, name, b, off, len, null, false, HIDDEN_CLASS|ACCESS_VM_ANNOTATIONS, null});
	}
	@Deprecated
	private static native Class<?> defineClass0(String name, ClassLoader cl, byte[] b, int len);

	private static void catchException(Throwable e) {
		String msg = JAVA_VERSION < 8 || JAVA_VERSION > 21 ? "您使用的JVM版本"+JAVA_VERSION+"不受支持!" : "未预料的内部错误";
		throw new InternalError(msg, e);
	}
}